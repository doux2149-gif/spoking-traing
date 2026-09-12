package com.example.speech.service.deepseek;

import com.example.speech.config.DeepSeekProperties;
import com.example.speech.service.AppSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekClient {
    private final DeepSeekProperties properties;
    private final AppSettingService appSettingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekClient(DeepSeekProperties properties,
                          AppSettingService appSettingService,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.appSettingService = appSettingService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 流式对话(onChunk接收文本,onComplete 接收文本与 usage token 统计)
     * UsageInfo 三个字段若接口未返回则为 0。
     */
    public void streamChat(List<ChatMessage> messages,
                           Consumer<String> onChunk,
                           BiConsumer<String, UsageInfo> onComplete) {
        String apiKey = resolveApiKey();
        String requestBody = buildRequestBody(messages, true);
        URI chatUri = properties.baseUrl().resolve("/chat/completions");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(chatUri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        StringBuilder fullText = new StringBuilder();
        UsageInfo lastUsage = new UsageInfo(0, 0, 0);

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("DeepSeek API 错误 (" + response.statusCode() + "): " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        String content = extractContent(data);
                        if (content != null && !content.isEmpty()) {
                            fullText.append(content);
                            onChunk.accept(content);
                        }
                        UsageInfo usage = extractUsage(data);
                        if (usage != null) {
                            lastUsage = usage;
                        }
                    }
                }
            }
            onComplete.accept(fullText.toString(), lastUsage);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("对话请求已中断", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("对话请求失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 旧版本兼容方法(忽略 usage),给旧代码调用。
     */
    public void streamChat(List<ChatMessage> messages, Consumer<String> onChunk, Runnable onComplete) {
        streamChat(messages, onChunk, (_text, _usage) -> onComplete.run());
    }

    /**
     * 单轮流式对话(支持 Function Calling)。
     * <p>content 增量实时推给 onContentDelta; 若模型请求调用工具(finish_reason=tool_calls),
     * 则累积并返回 toolCalls 列表, 由调用方执行工具后回填消息再次调用, 直到收敛。</p>
     *
     * @param messages       消息列表(可含 assistant.tool_calls 与 role=tool 消息)
     * @param tools          tools 定义数组(为 null 时不带 tools 字段)
     * @param onContentDelta 文本增量回调(可为 null)
     * @return 本轮结果: 累积文本 + 工具调用请求(若发起) + usage
     */
    public StreamRoundResult streamChatRound(List<AgentMessage> messages,
                                             ArrayNode tools,
                                             Consumer<String> onContentDelta) {
        String apiKey = resolveApiKey();
        String requestBody = buildAgentRequestBody(messages, tools, true);
        URI chatUri = properties.baseUrl().resolve("/chat/completions");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(chatUri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        StringBuilder fullText = new StringBuilder();
        UsageInfo lastUsage = new UsageInfo(0, 0, 0);
        Map<Integer, ToolCallAccumulator> toolAccumulators = new LinkedHashMap<>();
        String finishReason = null;

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("DeepSeek API 错误 (" + response.statusCode() + "): " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    var root = objectMapper.readTree(data);
                    var choices = root.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        var first = choices.get(0);
                        // content 增量
                        String content = first.path("delta").path("content").asText("");
                        if (!content.isEmpty()) {
                            fullText.append(content);
                            if (onContentDelta != null) {
                                onContentDelta.accept(content);
                            }
                        }
                        // tool_calls 分片累积(按 index 聚合)
                        var toolCallDeltas = first.path("delta").path("tool_calls");
                        if (toolCallDeltas.isArray()) {
                            for (var tc : toolCallDeltas) {
                                int idx = tc.path("index").asInt(0);
                                ToolCallAccumulator acc = toolAccumulators.computeIfAbsent(
                                        idx, k -> new ToolCallAccumulator());
                                String id = tc.path("id").asText("");
                                if (!id.isEmpty()) {
                                    acc.id = id;
                                }
                                var fn = tc.path("function");
                                String name = fn.path("name").asText("");
                                if (!name.isEmpty()) {
                                    acc.name = name;
                                }
                                String args = fn.path("arguments").asText("");
                                if (!args.isEmpty()) {
                                    acc.arguments.append(args);
                                }
                            }
                        }
                        String fr = first.path("finish_reason").asText("");
                        if (!fr.isEmpty()) {
                            finishReason = fr;
                        }
                    }
                    UsageInfo usage = extractUsage(data);
                    if (usage != null) {
                        lastUsage = usage;
                    }
                }
            }

            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallAccumulator acc : toolAccumulators.values()) {
                if (!acc.name.isEmpty()) {
                    toolCalls.add(new ToolCall(acc.id, acc.name, acc.arguments.toString()));
                }
            }
            // 收敛判断: finish_reason=tool_calls, 或缺失但已累积到工具调用且无文本
            boolean wantsToolCall = "tool_calls".equals(finishReason)
                    || (!toolCalls.isEmpty() && fullText.length() == 0);

            return new StreamRoundResult(fullText.toString(),
                    wantsToolCall ? toolCalls : List.of(), lastUsage);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("对话请求已中断", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("对话请求失败: " + exception.getMessage(), exception);
        }
    }

    /** 工具调用分片累积器 */
    private static final class ToolCallAccumulator {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }

    /** 带 usage 返回的非流式对话 */
    public ChatResponse chatWithUsage(List<ChatMessage> messages) {
        String apiKey = resolveApiKey();
        String requestBody = buildRequestBody(messages, false);
        URI chatUri = properties.baseUrl().resolve("/chat/completions");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(chatUri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("DeepSeek API 错误 (" + response.statusCode() + "): " + response.body());
            }
            var json = objectMapper.readTree(response.body());
            String content = json.path("choices").get(0).path("message").path("content").asText("");
            int pt = json.path("usage").path("prompt_tokens").asInt(0);
            int ct = json.path("usage").path("completion_tokens").asInt(0);
            int tt = json.path("usage").path("total_tokens").asInt(0);
            return new ChatResponse(content, new UsageInfo(pt, ct, tt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求已中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求失败: " + e.getMessage(), e);
        }
    }

    /** 兼容旧方法(不含 usage) */
    public String chat(List<ChatMessage> messages) {
        return chatWithUsage(messages).content();
    }

    private String resolveApiKey() {
        String apiKey = appSettingService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key 未配置，请联系管理员配置");
        }
        return apiKey;
    }

    private String buildRequestBody(List<ChatMessage> messages, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.put("max_tokens", properties.maxTokens());
        root.put("temperature", properties.temperature());
        root.put("stream", stream);

        ArrayNode messagesNode = root.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建请求", exception);
        }
    }

    /** 构建支持 tools / tool_calls / tool 消息的请求体 */
    private String buildAgentRequestBody(List<AgentMessage> messages, ArrayNode tools, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.put("max_tokens", properties.maxTokens());
        root.put("temperature", properties.temperature());
        root.put("stream", stream);
        if (tools != null && !tools.isEmpty()) {
            root.set("tools", tools);
        }

        ArrayNode messagesNode = root.putArray("messages");
        for (AgentMessage msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode toolCallsNode = msgNode.putArray("tool_calls");
                for (ToolCall tc : msg.toolCalls()) {
                    ObjectNode tcNode = toolCallsNode.addObject();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode fnNode = tcNode.putObject("function");
                    fnNode.put("name", tc.functionName());
                    fnNode.put("arguments", tc.arguments() == null ? "{}" : tc.arguments());
                }
            }
            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建请求", exception);
        }
    }

    private String extractContent(String json) {
        try {
            var root = objectMapper.readTree(json);
            var choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("delta").path("content").asText("");
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    /** 从流式 SSE 单条数据提取 usage (如果有) */
    private UsageInfo extractUsage(String json) {
        try {
            var root = objectMapper.readTree(json);
            var usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) return null;
            int pt = usage.path("prompt_tokens").asInt(-1);
            int ct = usage.path("completion_tokens").asInt(-1);
            int tt = usage.path("total_tokens").asInt(-1);
            if (pt < 0 && ct < 0 && tt < 0) return null;
            return new UsageInfo(Math.max(0, pt), Math.max(0, ct), Math.max(0, tt));
        } catch (Exception e) {
            return null;
        }
    }

    /** 单条对话消息 */
    public record ChatMessage(String role, String content) {}

    /** token 用量统计 */
    public record UsageInfo(int promptTokens, int completionTokens, int totalTokens) {}

    /** 非流式对话返回(内容+用量) */
    public record ChatResponse(String content, UsageInfo usage) {}

    /** 模型发起的工具调用请求 */
    public record ToolCall(String id, String functionName, String arguments) {}

    /** 支持工具调用的消息模型(role=tool 时 toolCallId 必填; assistant 可携带 toolCalls) */
    public record AgentMessage(String role, String content,
                               List<ToolCall> toolCalls, String toolCallId) {

        public static AgentMessage of(String role, String content) {
            return new AgentMessage(role, content, null, null);
        }

        /** assistant 消息 + 模型发起的工具调用请求 */
        public static AgentMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
            return new AgentMessage("assistant", content, toolCalls, null);
        }

        /** 工具执行结果消息(role=tool) */
        public static AgentMessage toolResult(String toolCallId, String result) {
            return new AgentMessage("tool", result, null, toolCallId);
        }
    }

    /** 单轮流式调用结果(content + 模型请求的工具调用 + usage) */
    public record StreamRoundResult(String content, List<ToolCall> toolCalls, UsageInfo usage) {}
}
