package com.example.speech.service.tools;

/**
 * 工具配置变更事件。
 * <p>当管理端 CRUD 工具时发布此事件, {@link ToolRegistry} 监听后立即刷新缓存,
 * 让新工具即时生效, 无需重启或等定时兜底。</p>
 */
public class ToolRefreshEvent {
    /** 触发原因: create / update / delete / toggle */
    private final String reason;
    /** 受影响的工具名(可为 null 表示批量) */
    private final String toolName;

    public ToolRefreshEvent(String reason, String toolName) {
        this.reason = reason;
        this.toolName = toolName;
    }

    public String reason() {
        return reason;
    }

    public String toolName() {
        return toolName;
    }
}
