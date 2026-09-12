package com.example.speech.service.tools;

import com.example.speech.entity.SysLlmTool;
import com.example.speech.mapper.SysLlmToolMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据库驱动的 HTTP 声明式工具提供者。
 * <p>从 sys_llm_tool 表读取 tool_type=HTTP 的工具定义, 执行时按配置
 * 调用外部 endpoint, 约定响应 {@code {"result": "..."}}。</p>
 * <p>endpoint 支持 {@code {key}} 路径参数模板(用 arguments 同名值替换);
 * GET 时剩余参数拼 query string, POST/PUT 时作为 JSON body。</p>
 * <p>容错: 任何异常(网络/超时/非 200/解析失败)都转为错误描述文本回传模型。</p>
 */
@Slf4j
@Component
public class HttpToolProvider implements ToolProvider {

    /** 路径参数模板: {key} */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    private final SysLlmToolMapper toolMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpToolProvider(SysLlmToolMapper toolMapper, ObjectMapper objectMapper) {
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return "http";
    }

    @Override
    public List<ToolDefinition> discover() {
        List<SysLlmTool> rows = toolMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLlmTool>()
                        .eq(SysLlmTool::getToolType, "HTTP"));
        List<ToolDefinition> out = new ArrayList<>();
        for (SysLlmTool row : rows) {
            out.add(new ToolDefinition(
                    row.getToolName(),
                    row.getDescription(),
                    row.getParametersSchema(),
                    source(),
                    row.getEnabled() != null && row.getEnabled() == 1));
        }
        return out;
    }

    @Override
    public String execute(String toolName, JsonNode arguments) {
        SysLlmTool row = toolMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLlmTool>()
                        .eq(SysLlmTool::getToolName, toolName)
                        .eq(SysLlmTool::getToolType, "HTTP"));
        if (row == null) {
            return "错误: HTTP 工具未找到 " + toolName;
        }
        if (row.getEnabled() == null || row.getEnabled() == 0) {
            return "错误: 工具 " + toolName + " 已禁用";
        }
        if (row.getHttpEndpoint() == null || row.getHttpEndpoint().isBlank()) {
            return "错误: 工具 " + toolName + " 未配置 endpoint";
        }

        int timeoutMs = row.getTimeoutMs() != null ? row.getTimeoutMs() : 5000;
        try {
            // 路径参数模板: endpoint 中的 {key} 用 arguments 同名值替换, 并从剩余参数中移除
            String endpoint = row.getHttpEndpoint();
            JsonNode effectiveArgs = arguments;
            if (arguments != null && arguments.isObject() && endpoint.contains("{")) {
                java.util.regex.Matcher m = PATH_PARAM_PATTERN.matcher(endpoint);
                StringBuffer sb = new StringBuffer();
                java.util.Set<String> used = new java.util.HashSet<>();
                while (m.find()) {
                    String key = m.group(1);
                    JsonNode v = arguments.get(key);
                    String rep = (v == null || v.isNull())
                            ? m.group(0)
                            : (v.isValueNode() ? v.asText() : v.toString());
                    if (v != null && !v.isNull()) {
                        used.add(key);
                    }
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(urlEncode(rep)));
                }
                m.appendTail(sb);
                endpoint = sb.toString();

                ObjectNode cleaned = objectMapper.createObjectNode();
                java.util.Set<String> usedFinal = used;
                arguments.fields().forEachRemaining(e -> {
                    if (!usedFinal.contains(e.getKey())) {
                        cleaned.set(e.getKey(), e.getValue());
                    }
                });
                effectiveArgs = cleaned;
            }

            // 构造请求体: 把剩余 arguments 作为 JSON 发送
            String body = (effectiveArgs == null || effectiveArgs.isNull() || effectiveArgs.isEmpty())
                    ? "{}"
                    : objectMapper.writeValueAsString(effectiveArgs);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");

            // 附加自定义请求头
            if (row.getHttpHeaders() != null && !row.getHttpHeaders().isBlank()) {
                JsonNode headers = objectMapper.readTree(row.getHttpHeaders());
                headers.fields().forEachRemaining(e ->
                        reqBuilder.header(e.getKey(), e.getValue().asText()));
            }

            String method = row.getHttpMethod() == null ? "POST" : row.getHttpMethod().toUpperCase();
            if ("GET".equals(method)) {
                // GET: 把剩余 arguments 拼接为 query string 追加到 URL
                String url = endpoint;
                if (effectiveArgs != null && effectiveArgs.isObject() && effectiveArgs.size() > 0) {
                    StringBuilder qs = new StringBuilder();
                    effectiveArgs.fields().forEachRemaining(e -> {
                        JsonNode v = e.getValue();
                        String val = v.isValueNode() ? v.asText() : v.toString();
                        qs.append(urlEncode(e.getKey())).append("=")
                                .append(urlEncode(val)).append("&");
                    });
                    String query = qs.substring(0, qs.length() - 1);
                    url = url + (url.contains("?") ? "&" : "?") + query;
                }
                reqBuilder.uri(URI.create(url)).GET();
            } else {
                reqBuilder.method(method, HttpRequest.BodyPublishers.ofString(body));
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() != 200) {
                return "工具 " + toolName + " 调用失败: HTTP " + resp.statusCode()
                        + " " + truncate(resp.body(), 200);
            }

            // 解析响应: 优先取 result 字段, 否则返回整个 body
            try {
                JsonNode respJson = objectMapper.readTree(resp.body());
                JsonNode resultNode = respJson.path("result");
                if (!resultNode.isMissingNode() && !resultNode.isNull()) {
                    return resultNode.isTextual() ? resultNode.asText() : resultNode.toString();
                }
                return resp.body();
            } catch (Exception parseEx) {
                return resp.body();
            }
        } catch (Exception e) {
            log.warn("HTTP 工具 {} 执行异常: {}", toolName, e.getMessage());
            return "工具 " + toolName + " 执行异常: " + e.getMessage();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** URL 参数编码 */
    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 用于管理端试运行: 直接传入参数 JSON 字符串, 返回执行结果 */
    public String testExecute(String toolName, String argumentsJson) {
        try {
            JsonNode args = (argumentsJson == null || argumentsJson.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
            return execute(toolName, args);
        } catch (Exception e) {
            return "参数解析失败: " + e.getMessage();
        }
    }
}
