package com.example.speech.service.tools;

import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.AgentMessage;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import com.example.speech.service.deepseek.DeepSeekClient.StreamRoundResult;
import com.example.speech.service.deepseek.DeepSeekClient.ToolCall;
import com.example.speech.service.deepseek.DeepSeekClient.UsageInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具调用收敛循环(Agent Loop)。
 * <p>流程: LLM(带 tools 定义) → 返回 tool_calls → 执行工具 → 结果回填消息列表
 * → 再次调用 LLM → 循环, 直到模型不再请求工具(收敛)并给出最终回答。</p>
 * <p>安全阀: 最多 {@value #MAX_TOOL_ROUNDS} 轮, 超限后去掉 tools 强制模型直接作答。</p>
 */
@Slf4j
@Component
public class ToolCallAgentService {

    /** 工具调用轮数上限, 防止无限循环 */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final DeepSeekClient deepSeekClient;
    private final ToolRegistry toolRegistry;

    public ToolCallAgentService(DeepSeekClient deepSeekClient, ToolRegistry toolRegistry) {
        this.deepSeekClient = deepSeekClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 带工具调用的流式对话。
     *
     * @param baseMessages 初始消息列表(system + 历史 + 本轮用户消息)
     * @param onTextChunk  文本增量回调(流式推给前端)
     * @param onToolEvent  工具执行事件回调(可用于前端提示, 可为 null)
     * @param onComplete   最终完成回调(完整回答文本 + 多轮累计 usage)
     */
    public void streamChatWithTools(List<ChatMessage> baseMessages,
                                    Consumer<String> onTextChunk,
                                    Consumer<ToolExecution> onToolEvent,
                                    BiConsumer<String, UsageInfo> onComplete) {
        List<AgentMessage> messages = new ArrayList<>();
        for (ChatMessage m : baseMessages) {
            messages.add(AgentMessage.of(m.role(), m.content()));
        }

        UsageInfo totalUsage = new UsageInfo(0, 0, 0);

        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            StreamRoundResult result = deepSeekClient.streamChatRound(
                    messages, toolRegistry.toolsJson(), onTextChunk);
            totalUsage = addUsage(totalUsage, result.usage());

            // 收敛: 模型未请求工具, 直接输出最终回答
            if (result.toolCalls().isEmpty()) {
                onComplete.accept(result.content(), totalUsage);
                return;
            }

            log.info("第 {} 轮工具调用: {}", round,
                    result.toolCalls().stream().map(ToolCall::functionName).toList());

            // 记录 assistant 的工具调用请求
            messages.add(AgentMessage.assistantWithToolCalls(result.content(), result.toolCalls()));

            // 逐个执行工具, 结果以 role=tool 消息回填
            for (ToolCall call : result.toolCalls()) {
                String toolResult = toolRegistry.dispatch(call.functionName(), call.arguments());
                messages.add(AgentMessage.toolResult(call.id(), toolResult));
                if (onToolEvent != null) {
                    onToolEvent.accept(new ToolExecution(call.functionName(), call.arguments(), toolResult));
                }
            }
        }

        // 达到上限仍未收敛: 去掉 tools 强制模型直接回答
        log.warn("工具调用达到 {} 轮上限, 去除 tools 强制收敛", MAX_TOOL_ROUNDS);
        StreamRoundResult finalResult = deepSeekClient.streamChatRound(messages, null, onTextChunk);
        totalUsage = addUsage(totalUsage, finalResult.usage());
        onComplete.accept(finalResult.content(), totalUsage);
    }

    /** usage 累加 */
    private UsageInfo addUsage(UsageInfo a, UsageInfo b) {
        return new UsageInfo(
                a.promptTokens() + Math.max(0, b.promptTokens()),
                a.completionTokens() + Math.max(0, b.completionTokens()),
                a.totalTokens() + Math.max(0, b.totalTokens()));
    }

    /** 一次工具执行事件(用于前端展示/日志) */
    public record ToolExecution(String toolName, String arguments, String result) {}
}
