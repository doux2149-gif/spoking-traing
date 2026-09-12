package com.example.speech.service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码级工具提供者。
 * <p>自动收集 Spring 容器中所有 {@link LlmTool} Bean, 包装成统一的
 * {@link ToolDefinition}。代码工具永远 enabled=true, 不可由管理端禁用。</p>
 */
@Slf4j
@Component
public class SpringBeanToolProvider implements ToolProvider {

    private final List<LlmTool> tools;
    private final List<ToolDefinition> cachedDefinitions;

    public SpringBeanToolProvider(List<LlmTool> tools) {
        this.tools = tools;
        this.cachedDefinitions = new ArrayList<>();
        for (LlmTool t : tools) {
            cachedDefinitions.add(new ToolDefinition(
                    t.name(), t.description(), t.parameterSchema(), source(), true));
        }
        log.info("SpringBeanToolProvider 已加载 {} 个代码工具: {}",
                tools.size(),
                tools.stream().map(LlmTool::name).toList());
    }

    @Override
    public String source() {
        return "spring";
    }

    @Override
    public List<ToolDefinition> discover() {
        return cachedDefinitions;
    }

    @Override
    public String execute(String toolName, JsonNode arguments) {
        for (LlmTool t : tools) {
            if (t.name().equals(toolName)) {
                try {
                    return t.execute(arguments);
                } catch (Exception e) {
                    log.warn("代码工具 {} 执行失败: {}", toolName, e.getMessage());
                    return "工具执行失败: " + e.getMessage();
                }
            }
        }
        return "错误: 代码工具未找到 " + toolName;
    }
}
