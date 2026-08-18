package com.example.speech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.Conversation;
import com.example.speech.entity.ConversationMessage;
import com.example.speech.entity.GrammarCorrection;
import com.example.speech.entity.SysUser;
import com.example.speech.mapper.ConversationMapper;
import com.example.speech.mapper.ConversationMessageMapper;
import com.example.speech.mapper.SysUserMapper;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.LlmUsageService;
import com.example.speech.service.context.ConversationContextBuilder;
import com.example.speech.service.context.ConversationContextBuilder.ContextBuildResult;
import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import com.example.speech.service.deepseek.DeepSeekClient.ChatResponse;
import com.example.speech.util.GrammarStreamParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    /** 英文发音人集合,命中时启用语法纠错 */
    private static final Set<String> ENGLISH_VCN = Set.of("catherine", "henry");

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ConversationContextBuilder contextBuilder;
    private final LlmUsageService usageService;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final SysUserMapper userMapper;

    public ChatController(DeepSeekClient deepSeekClient,
                          ObjectMapper objectMapper,
                          ConversationContextBuilder contextBuilder,
                          LlmUsageService usageService,
                          ConversationMapper conversationMapper,
                          ConversationMessageMapper messageMapper,
                          SysUserMapper userMapper) {
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
        this.contextBuilder = contextBuilder;
        this.usageService = usageService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            SseEmitter errEmitter = new SseEmitter();
            Thread.startVirtualThread(() -> {
                try {
                    errEmitter.send(SseEmitter.event().name("error").data("消息列表不能为空"));
                } catch (Exception ignored) {
                }
                errEmitter.complete();
            });
            return errEmitter;
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            SseEmitter errEmitter = new SseEmitter();
            Thread.startVirtualThread(() -> {
                try { errEmitter.send(SseEmitter.event().name("error").data("未登录")); } catch (Exception ignored) {}
                errEmitter.complete();
            });
            return errEmitter;
        }

        // 场景练习模式:有 scenePrompt 时启用场景 system prompt + 语法纠错
        boolean hasScene = request.scenePrompt() != null && !request.scenePrompt().isBlank();
        final boolean enableGrammar = hasScene || (request.vcn() != null && ENGLISH_VCN.contains(request.vcn()));

        // 加载上下文相关数据:context_summary + user preferences_json
        String contextSummary = null;
        String userPrefsJson = null;
        if (request.conversationId() != null) {
            try {
                Conversation conv = conversationMapper.selectById(request.conversationId());
                if (conv != null && (userId.equals(conv.getUserId()))) {
                    contextSummary = conv.getContextSummary();
                }
            } catch (Exception e) {
                log.warn("加载 conversation 上下文失败: {}", e.getMessage());
            }
        }
        try {
            SysUser user = userMapper.selectById(userId);
            if (user != null) userPrefsJson = user.getPreferencesJson();
        } catch (Exception e) {
            log.warn("加载用户偏好失败: {}", e.getMessage());
        }

        // 构造上下文 (分层:system + 摘要 + 最近窗口)
        ContextBuildResult ctxResult = contextBuilder.build(
                enableGrammar,
                request.scenePrompt(),
                contextSummary,
                userPrefsJson,
                request.messages()
        );
        List<ChatMessage> llmMessages = ctxResult.messages();
        final int estimatedTokens = ctxResult.estimatedTokens();
        final boolean truncated = ctxResult.truncated();

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> emitter.complete());

        final Long fUserId = userId;
        final Long fConvId = request.conversationId();
        final boolean fNeedSummarize = ctxResult.shouldSummarize();
        final List<ChatMessage> fAllMessages = request.messages();
        final String fScenePrompt = request.scenePrompt();
        final String fContextSummary = contextSummary;

        Thread.startVirtualThread(() -> {
            GrammarStreamParser parser = enableGrammar
                    ? new GrammarStreamParser(objectMapper)
                    : null;

            // 先给前端推送一个 meta 事件:告诉前端"是否截断了/估算token/是否需要摘要"(仅信息,可选)
            try {
                ObjectNode meta = objectMapper.createObjectNode();
                meta.put("truncated", truncated);
                meta.put("estimatedTokens", estimatedTokens);
                meta.put("needSummarize", fNeedSummarize);
                if (fConvId != null) meta.put("conversationId", fConvId);
                emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));
            } catch (Exception ignored) {
            }

            try {
                deepSeekClient.streamChat(
                        llmMessages,
                        chunk -> {
                            try {
                                String textPart = parser != null ? parser.feed(chunk) : chunk;
                                if (textPart != null && !textPart.isEmpty()) {
                                    emitter.send(SseEmitter.event().name("text").data(textPart));
                                }
                            } catch (Exception ignored) {
                            }
                        },
                        (fullText, usage) -> {
                            // 1) 记录 token 用量(异步)
                            try {
                                int pt = usage.promptTokens() > 0 ? usage.promptTokens() : estimatedTokens;
                                int ct = usage.completionTokens();
                                int tt = usage.totalTokens() > 0 ? usage.totalTokens() : (pt + ct);
                                usageService.logUsage(fUserId, fConvId, "chat", pt, ct, tt);
                            } catch (Exception e) {
                                log.warn("写入 llm_usage_log 失败: {}", e.getMessage());
                            }

                            // 2) 推送 grammar + done
                            try {
                                if (parser != null) {
                                    GrammarCorrection grammar = parser.finish();
                                    boolean hasError = !grammar.noError();
                                    boolean hasSuggestion = grammar.suggestion() != null
                                            && grammar.suggestion().level() != null
                                            && !"none".equals(grammar.suggestion().level());
                                    if (hasError || hasSuggestion) {
                                        emitter.send(SseEmitter.event().name("grammar")
                                                .data(objectMapper.writeValueAsString(grammar)));
                                    }
                                }
                                // 3) 触发异步摘要压缩 (若本轮需要)
                                if (fNeedSummarize && fConvId != null) {
                                    triggerSummarizeAsync(fUserId, fConvId, fScenePrompt,
                                            fAllMessages, fContextSummary);
                                }
                                emitter.send(SseEmitter.event().name("done").data(""));
                            } catch (Exception ignored) {
                            }
                            emitter.complete();
                        }
                );
            } catch (Exception exception) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(exception.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 异步:对需要压缩的旧对话生成/更新 context_summary 并写回 conversation 表
     * 失败不影响对话,只是下一轮不会有新摘要。
     */
    @Async
    public void triggerSummarizeAsync(Long userId, Long conversationId, String scenePrompt,
                                       List<ChatMessage> allMessages, String existingSummary) {
        try {
            // 1) 从消息中剥离 system 和纠错 JSON,得到纯 user/assistant 对话
            List<ChatMessage> nonSystem = new ArrayList<>();
            for (ChatMessage m : allMessages) {
                if (!"system".equals(m.role())) {
                    String content = ConversationContextBuilder.stripGrammarJson(m.content());
                    nonSystem.add(new ChatMessage(m.role(), content));
                }
            }
            // 2) 构建摘要输入:system + 被挤出窗口的那部分(如果有existingSummary,可结合一起)
            List<ChatMessage> summaryInput = contextBuilder.buildSummaryPromptMessages(nonSystem);
            if (summaryInput == null) return;

            // 若已有摘要,把它加在 user 消息头部,让 LLM 增量
            if (existingSummary != null && !existingSummary.isBlank() && summaryInput.size() >= 2) {
                ChatMessage lastUser = summaryInput.get(summaryInput.size() - 1);
                String merged = "之前的摘要:\n" + existingSummary
                        + "\n\n新增对话:\n" + lastUser.content()
                        + "\n\n请基于之前摘要 + 新增对话,给出新的完整摘要(仍 200 字内)。";
                summaryInput.set(summaryInput.size() - 1, new ChatMessage(lastUser.role(), merged));
            }

            ChatResponse resp = deepSeekClient.chatWithUsage(summaryInput);
            String newSummary = resp.content() == null ? null : resp.content().trim();
            if (newSummary == null || newSummary.isEmpty()) return;

            // 3) 记录本次摘要的 token 用量
            try {
                usageService.logUsage(userId, conversationId, "summary",
                        resp.usage().promptTokens(),
                        resp.usage().completionTokens(),
                        resp.usage().totalTokens());
            } catch (Exception ignored) {
            }

            // 4) 写回 conversation.context_summary
            try {
                Conversation conv = conversationMapper.selectById(conversationId);
                if (conv != null && userId.equals(conv.getUserId())) {
                    conv.setContextSummary(newSummary);
                    conversationMapper.updateById(conv);
                }
            } catch (Exception e) {
                log.warn("写回 context_summary 失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("上下文摘要压缩失败(convId={}): {}", conversationId, e.getMessage());
        }
    }

    /**
     * 聊天请求。
     * @param messages 完整对话消息(按时间升序)
     * @param vcn 发音人(可选,英文vcn触发语法纠错)
     * @param scenePrompt 场景设定(可选,有值触发场景模式)
     * @param conversationId 会话ID(可选,用于加载/更新context_summary与记录usage)
     */
    public record ChatRequest(List<ChatMessage> messages, String vcn, String scenePrompt, Long conversationId) {}
}
