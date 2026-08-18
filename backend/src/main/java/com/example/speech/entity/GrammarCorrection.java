package com.example.speech.entity;

import java.util.List;

/**
 * 语法纠错结果实体。
 * 由 LLM 在聊天回复末尾以 ##GRAMMAR_JSON##...##END_JSON## 包裹输出，
 * 后端解析后通过 SSE grammar 事件推送给前端。
 *
 * @param noError      是否无错误；true 时其余字段可为 null
 * @param original     用户原始英文句子
 * @param correctText  修正后的完整句子
 * @param errors       错误明细列表
 * @param speechText   适合朗读的中英文结合口语化文本（TTS 用）
 * @param suggestion   表达建议（无语法错误时给出更地道的说法）
 */
public record GrammarCorrection(
        boolean noError,
        String original,
        String correctText,
        List<GrammarError> errors,
        String speechText,
        Suggestion suggestion
) {

    /**
     * 单条错误明细。
     *
     * @param wrongText   错误片段
     * @param correctText 修改后的片段
     * @param reason      中文简短错误原因
     */
    public record GrammarError(
            String wrongText,
            String correctText,
            String reason
    ) {}

    /**
     * 表达建议：用户句子正确但不够地道时，提供更好的说法。
     *
     * @param level        建议等级：none/better/advanced
     * @param original     用户原句
     * @param alternatives 2-3种更地道的替代表达
     * @param tip          中文简短解释为什么更好
     * @param speechText   语音朗读文本（TTS 用）
     */
    public record Suggestion(
            String level,
            String original,
            List<String> alternatives,
            String tip,
            String speechText
    ) {}
}
