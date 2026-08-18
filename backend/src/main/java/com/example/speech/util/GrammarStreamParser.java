package com.example.speech.util;

import com.example.speech.entity.GrammarCorrection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 流式语法纠错 JSON 解析器(状态机)。
 *
 * 用于 SSE 流式场景。LLM 输出格式为:
 *   [聊天文本]##GRAMMAR_JSON##{...}##END_JSON##
 *
 * 本解析器边收边剥离标记:
 * - 标记前的文本通过 {@link #feed(String)} 返回,供 Controller 作为 text 事件流式推送;
 * - 标记间的 JSON 缓冲到内部,流结束后通过 {@link #finish()} 解析为 {@link GrammarCorrection}。
 *
 * 解析失败(标记缺失、JSON 损坏)时 {@link #finish()} 返回 noError=true,不抛异常,
 * 保证不会因为 LLM 输出格式异常而中断主对话流程。
 *
 * 线程不安全:每个 SSE 请求应 new 一个实例。
 */
public class GrammarStreamParser {

    /** JSON 块开始标记 */
    public static final String GRAMMAR_START = "##GRAMMAR_JSON##";

    /** JSON 块结束标记 */
    public static final String GRAMMAR_END = "##END_JSON##";

    /** 兜底空结果,用于解析失败或无标记场景 */
    private static final GrammarCorrection NO_ERROR = new GrammarCorrection(
            true, null, null, List.of(), null, null);

    private enum Mode { TEXT, GRAMMAR, DONE }

    private final ObjectMapper objectMapper;

    private Mode mode = Mode.TEXT;

    /** GRAMMAR_MODE 下缓冲的 JSON 文本(含可能的 ##END_JSON##) */
    private final StringBuilder grammarBuffer = new StringBuilder();

    /** TEXT_MODE 下尾部疑似 GRAMMAR_START 前缀的残留文本,等待下一片 chunk 拼接判断 */
    private String textTail = "";

    public GrammarStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 喂入一段流式 chunk,返回当前可作为 text 事件推送的纯文本。
     * 进入 GRAMMAR_MODE 后返回空字符串(后续内容缓冲到 grammarBuffer)。
     */
    public String feed(String chunk) {
        if (mode == Mode.DONE || chunk == null || chunk.isEmpty()) {
            return "";
        }
        if (mode == Mode.GRAMMAR) {
            appendGrammar(chunk);
            return "";
        }
        // TEXT_MODE:与上次残留的 textTail 拼接后扫描 GRAMMAR_START
        String buffer = textTail + chunk;
        textTail = "";

        int startIdx = buffer.indexOf(GRAMMAR_START);
        if (startIdx >= 0) {
            String textPart = buffer.substring(0, startIdx);
            String after = buffer.substring(startIdx + GRAMMAR_START.length());
            mode = Mode.GRAMMAR;
            appendGrammar(after);
            return textPart;
        }

        // 未找到完整标记,检查尾部是否是 GRAMMAR_START 的前缀(防止 chunk 在标记中间断开)
        int prefixLen = longestSuffixPrefix(buffer, GRAMMAR_START);
        if (prefixLen > 0) {
            textTail = buffer.substring(buffer.length() - prefixLen);
            return buffer.substring(0, buffer.length() - prefixLen);
        }
        return buffer;
    }

    /**
     * 流结束后调用,返回解析到的 {@link GrammarCorrection}。
     * 解析失败、无标记、无错误时返回 noError=true 的兜底结果。
     */
    public GrammarCorrection finish() {
        if (mode == Mode.TEXT) {
            // 从未进入 GRAMMAR_MODE,可能 LLM 未输出标记
            return NO_ERROR;
        }
        return parseGrammarBuffer();
    }

    private void appendGrammar(String chunk) {
        grammarBuffer.append(chunk);
        int endIdx = grammarBuffer.indexOf(GRAMMAR_END);
        if (endIdx >= 0) {
            // 截取 ##END_JSON## 之前的 JSON 内容,丢弃其后所有文本
            String json = grammarBuffer.substring(0, endIdx);
            grammarBuffer.setLength(0);
            grammarBuffer.append(json);
            mode = Mode.DONE;
        }
    }

    private GrammarCorrection parseGrammarBuffer() {
        String json = grammarBuffer.toString().trim();
        if (json.isEmpty()) {
            return NO_ERROR;
        }
        try {
            GrammarCorrection result = objectMapper.readValue(json, GrammarCorrection.class);
            return result != null ? result : NO_ERROR;
        } catch (Exception ignored) {
            // JSON 损坏,静默兜底
            return NO_ERROR;
        }
    }

    /**
     * 返回 text 尾部与 marker 前缀的最长匹配长度。
     * 例如 text="abc##G", marker="##GRAMMAR_JSON##",返回 3("##G" 是 marker 前缀)。
     */
    private static int longestSuffixPrefix(String text, String marker) {
        int maxLen = Math.min(text.length(), marker.length() - 1);
        for (int len = maxLen; len > 0; len--) {
            if (text.regionMatches(text.length() - len, marker, 0, len)) {
                return len;
            }
        }
        return 0;
    }
}
