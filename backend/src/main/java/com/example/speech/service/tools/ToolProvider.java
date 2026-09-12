package com.example.speech.service.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具来源提供者 SPI。
 * <p>不同来源(Spring Bean / DB HTTP / 脚本)各自实现, 由
 * {@link ToolRegistry} 统一收集、生成 tools 数组、按名分发执行。</p>
 */
public interface ToolProvider {

    /** 来源标识: "spring" / "http" / "script" */
    String source();

    /**
     * 发现当前可用的所有工具定义。
     * <p>调用时机: {@link ToolRegistry#refresh()} 时按 version 变化重新拉取。
     * 实现应尽量轻量(命中缓存或读一次 DB), 不要执行具体工具。</p>
     */
    java.util.List<ToolDefinition> discover();

    /**
     * 执行工具。
     * <p>任何异常都应被实现包装为错误描述文本返回(而不是抛出),
     * 让模型据此向用户解释, 避免整轮对话请求失败。</p>
     *
     * @param toolName  工具名(必须在本 provider discover 出的列表中)
     * @param arguments 模型给出的参数 JSON
     * @return 返回给模型的文本结果
     */
    String execute(String toolName, JsonNode arguments);
}
