package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.LlmUsageService;
import com.example.speech.service.context.ConversationContextBuilder;
import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import com.example.speech.service.deepseek.DeepSeekClient.ChatResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 回答翻译接口。
 * <p>把 AI 的英文回答翻译成自然中文, 供前端"翻译"按钮使用。
 * 使用 DeepSeek 非流式调用, 翻译前剥离 grammar JSON 块。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private static final String TRANSLATE_SYSTEM_PROMPT =
            "你是翻译引擎。把用户发来的英文文本翻译成自然、地道、口语化的简体中文。"
                    + "只输出译文, 不要解释, 不要加任何前缀或引号。";

    /** 单次翻译文本长度上限(保护 token 消耗) */
    private static final int MAX_TRANSLATE_CHARS = 5000;

    private final DeepSeekClient deepSeekClient;
    private final LlmUsageService usageService;

    @PostMapping
    public Result<Map<String, String>> translate(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            return Result.error(400, "text 不能为空");
        }
        // 剥离纠错 JSON 块, 只翻译正文
        String plain = ConversationContextBuilder.stripGrammarJson(text).trim();
        if (plain.isEmpty()) {
            return Result.error(400, "无可翻译内容");
        }
        if (plain.length() > MAX_TRANSLATE_CHARS) {
            plain = plain.substring(0, MAX_TRANSLATE_CHARS);
        }

        try {
            ChatResponse resp = deepSeekClient.chatWithUsage(List.of(
                    new ChatMessage("system", TRANSLATE_SYSTEM_PROMPT),
                    new ChatMessage("user", plain)
            ));
            String translation = resp.content() == null ? "" : resp.content().trim();
            if (translation.isEmpty()) {
                return Result.error(500, "翻译结果为空");
            }
            // token 用量记录(type=translate)
            try {
                usageService.logUsage(userId, null, "translate",
                        resp.usage().promptTokens(),
                        resp.usage().completionTokens(),
                        resp.usage().totalTokens());
            } catch (Exception ignored) {
            }
            return Result.success(Map.of("translation", translation));
        } catch (Exception e) {
            log.warn("翻译失败: {}", e.getMessage());
            return Result.error(500, "翻译失败, 请稍后重试");
        }
    }
}
