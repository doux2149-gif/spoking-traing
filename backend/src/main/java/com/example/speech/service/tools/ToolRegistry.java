package com.example.speech.service.tools;

import com.example.speech.mapper.SysLlmToolMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 动态工具注册中心(替代旧的静态 ToolRegistry)。
 * <p>统一管理两类工具来源:
 * <ul>
 *   <li>{@code spring} — 代码级工具(实现 {@link LlmTool} 接口的 Bean)</li>
 *   <li>{@code http}   — 数据库驱动的 HTTP 声明式工具(管理页面 CRUD)</li>
 * </ul>
 * 通过版本号(version)感知 DB 变更, 按需重建内存缓存;
 * 触发时机: 启动时 / 收到 {@link ToolRefreshEvent} / 定时兜底(60s)。</p>
 * <p>{@link ToolCallAgentService} 调用 {@link #toolsJson()} 和 {@link #dispatch},
 * 对底层来源完全无感。</p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ObjectMapper objectMapper;
    private final SysLlmToolMapper toolMapper;
    private final List<ToolProvider> providers;

    /** 缓存: toolName → (provider + definition) */
    private volatile Map<String, Entry> cache = Map.of();
    /** 上次加载时的 DB version, -1 表示尚未加载 */
    private volatile long loadedVersion = -1;

    public ToolRegistry(ObjectMapper objectMapper,
                        SysLlmToolMapper toolMapper,
                        List<ToolProvider> providers) {
        this.objectMapper = objectMapper;
        this.toolMapper = toolMapper;
        this.providers = providers;
    }

    @PostConstruct
    public void init() {
        refresh(true);
    }

    /** 接收 CRUD 事件后立即刷新(强制, 因为 create 时 version 可能不变) */
    @EventListener
    public void onToolRefresh(ToolRefreshEvent event) {
        log.info("收到工具刷新事件: reason={}, tool={}", event.reason(), event.toolName());
        refresh(true);
    }

    /** 定时兜底: 60s 检查一次 DB version, 多实例部署也能最终一致 */
    @Scheduled(fixedDelay = 60000)
    public void scheduledRefresh() {
        refresh(false);
    }

    /**
     * 检查 DB version, 若有变化则重建缓存。
     * <p>force=false 时幂等: version 未变则直接返回, 不做任何 IO;
     * force=true 时无条件重建(用于事件/启动触发)。</p>
     */
    public synchronized void refresh(boolean force) {
        try {
            long dbVersion = toolMapper.selectMaxVersion();
            if (!force && dbVersion == loadedVersion) {
                return;
            }
            log.info("工具缓存刷新: version {} -> {}", loadedVersion, dbVersion);

            Map<String, Entry> next = new LinkedHashMap<>();
            for (ToolProvider provider : providers) {
                try {
                    List<ToolDefinition> defs = provider.discover();
                    for (ToolDefinition def : defs) {
                        Entry existing = next.put(def.name(), new Entry(provider, def));
                        if (existing != null) {
                            log.warn("工具名冲突: {} (source={} 覆盖 source={})",
                                    def.name(), def.source(), existing.definition().source());
                        }
                    }
                    log.info("已加载 {} 个工具来自 source={}", defs.size(), provider.source());
                } catch (Exception e) {
                    log.warn("provider {} discover 失败: {}", provider.source(), e.getMessage());
                }
            }
            cache = Map.copyOf(next);
            loadedVersion = dbVersion;

            log.info("工具注册完成, 共 {} 个工具: {}",
                    next.size(),
                    next.keySet());
        } catch (Exception e) {
            log.error("工具缓存刷新失败: {}", e.getMessage(), e);
        }
    }

    /** 是否有可用工具 */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * 生成 DeepSeek 请求体的 tools 数组。
     * <p>只包含 enabled=true 的工具。无工具返回 null(请求中不带 tools 字段)。</p>
     */
    public ArrayNode toolsJson() {
        if (cache.isEmpty()) {
            return null;
        }
        ArrayNode tools = objectMapper.createArrayNode();
        for (Entry e : cache.values()) {
            if (!e.definition().enabled()) {
                continue;
            }
            ObjectNode item = tools.addObject();
            item.put("type", "function");
            ObjectNode fn = item.putObject("function");
            fn.put("name", e.definition().name());
            fn.put("description", e.definition().description());
            try {
                String schema = e.definition().parametersSchema();
                fn.set("parameters", (schema == null || schema.isBlank())
                        ? objectMapper.createObjectNode()
                        : objectMapper.readTree(schema));
            } catch (Exception ex) {
                log.warn("工具 {} schema 解析失败, 用空 schema: {}", e.definition().name(), ex.getMessage());
                fn.putObject("parameters");
            }
        }
        return tools.size() == 0 ? null : tools;
    }

    /**
     * 按工具名分发执行。
     * <p>任何异常都转为错误描述文本, 让模型据此向用户解释, 不让整轮对话请求 500。</p>
     */
    public String dispatch(String name, String argumentsJson) {
        Entry e = cache.get(name);
        if (e == null) {
            return "错误: 未找到名为 " + name + " 的工具";
        }
        if (!e.definition().enabled()) {
            return "错误: 工具 " + name + " 已禁用";
        }
        try {
            JsonNode args = (argumentsJson == null || argumentsJson.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(argumentsJson);
            return e.provider().execute(name, args);
        } catch (Exception ex) {
            log.warn("工具 {} 参数解析失败: {}", name, ex.getMessage());
            return "工具参数解析失败: " + ex.getMessage();
        }
    }

    /** 缓存项: 工具来源 + 定义 */
    private record Entry(ToolProvider provider, ToolDefinition definition) {}
}
