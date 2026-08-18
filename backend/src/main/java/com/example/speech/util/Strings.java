package com.example.speech.util;

/**
 * 字符串通用工具类。
 * 提供项目内多处复用的字符串处理逻辑，
 * 避免在 Service / Handler 中重复实现。
 */
public final class Strings {

    private Strings() {
        // 工具类禁止实例化
    }

    /**
     * 截断字符串到指定最大长度，超长部分丢弃。
     * 入参为 null 时直接返回 null，保持原语义。
     *
     * @param value     原始字符串
     * @param maxLength 最大长度（&gt;=0）
     * @return 截断后的字符串
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
