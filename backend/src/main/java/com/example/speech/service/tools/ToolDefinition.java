package com.example.speech.service.tools;

/**
 * 统一工具定义(与来源无关)。
 * <p>由各 {@link ToolProvider} 在 discover() 时产出, 经 {@link ToolRegistry}
 * 汇总后生成 DeepSeek 请求的 tools 数组。</p>
 *
 * @param name            工具名
 * @param description     描述
 * @param parametersSchema 参数 JSON Schema(字符串形式, 可为 null/空 表示无参数)
 * @param source          来源标识(对应 ToolProvider.source())
 * @param enabled         是否启用(false 时不会被模型看到, 也不会被执行)
 */
public record ToolDefinition(String name,
                             String description,
                             String parametersSchema,
                             String source,
                             boolean enabled) {
}
