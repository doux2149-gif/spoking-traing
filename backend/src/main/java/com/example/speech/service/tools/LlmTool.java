package com.example.speech.service.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * LLM 可调用工具接口。
 * <p>实现类标注 {@code @Component} 后会被 {@link ToolRegistry} 自动收集注册,
 * 无需额外配置。每个工具建议放在独立子包中(如 {@code tools.datetime})。</p>
 */
public interface LlmTool {

    /** 工具名(供模型调用), 小写下划线命名, 如 get_current_datetime */
    String name();

    /** 工具描述(告诉模型什么场景该调用此工具, 描述越明确触发越准确) */
    String description();

    /** 参数 JSON Schema 字符串, 无参数时返回空对象 schema */
    String parameterSchema();

    /**
     * 执行工具。
     * @param arguments 模型给出的参数(JSON 对象)
     * @return 返回给模型的结果文本(会作为 role=tool 消息回填给模型)
     */
    String execute(JsonNode arguments);
}
