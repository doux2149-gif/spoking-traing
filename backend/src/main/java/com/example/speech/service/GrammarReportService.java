package com.example.speech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.speech.entity.ConversationMessage;
import com.example.speech.entity.GrammarCorrection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯规则聚合: 基于 conversation_message.grammar_json 计算雅思四维度分数。
 * 零额外 LLM 调用; suggestion.tip 里已经直接出现"语法多样性"/"词汇多样性",
 * 直接按关键词归类即可。
 */
@Service
@RequiredArgsConstructor
public class GrammarReportService {

    private final ObjectMapper objectMapper;

    /** 雅思维度分类 (内部 key) */
    public static final String DIM_GRAMMAR = "grammar";
    public static final String DIM_VOCAB = "vocabulary";
    public static final String DIM_FLUENCY = "fluency";

    /** 错误 reason 关键词 → 维度 */
    private static final List<KeywordRule> ERROR_RULES = List.of(
            new KeywordRule(List.of("时态", "tense", "过去时", "现在时", "完成时"), DIM_GRAMMAR, "时态错误"),
            new KeywordRule(List.of("冠词", "article", "a/an/the", "单复数"), DIM_GRAMMAR, "冠词/名词单复数"),
            new KeywordRule(List.of("介词", "preposition"), DIM_GRAMMAR, "介词搭配"),
            new KeywordRule(List.of("主谓一致", "subject-verb"), DIM_GRAMMAR, "主谓一致"),
            new KeywordRule(List.of("语序", "word order", "语序错误"), DIM_GRAMMAR, "语序"),
            new KeywordRule(List.of("词汇", "单词", "搭配", "vocabulary", "短语"), DIM_VOCAB, "词汇/搭配")
    );

    /** suggestion.tip 关键词 → 维度 */
    private static final List<KeywordRule> SUGGESTION_RULES = List.of(
            new KeywordRule(List.of("语法多样性", "句式多样性", "名词性从句", "定语从句", "状语从句",
                    "虚拟语气", "倒装", "复合句", "从句"), DIM_GRAMMAR, "语法多样性"),
            new KeywordRule(List.of("词汇多样性", "词汇搭配", "地道说法", "cuisine", "vocabulary"), DIM_VOCAB, "词汇多样性")
    );

    /** 聚合返回: 分数 + topErrors + topSuggestions + 统计量 */
    public Aggregation aggregate(List<ConversationMessage> messages) {
        List<GrammarCorrection> grammars = new ArrayList<>();
        int userTurns = 0;
        for (ConversationMessage m : messages) {
            if (!"user".equals(m.getRole())) continue;
            userTurns++;
            GrammarCorrection gc = parse(m.getGrammarJson());
            if (gc != null && (gc.errors() != null && !gc.errors().isEmpty()
                    || gc.suggestion() != null && StringUtils.hasText(gc.suggestion().level()))) {
                grammars.add(gc);
            }
        }

        int grammarErrorCount = 0;
        int vocabErrorCount = 0;
        int grammarBetter = 0;
        int grammarAdvanced = 0;
        int vocabBetter = 0;
        int vocabAdvanced = 0;

        List<TopErrorItem> topErrors = new ArrayList<>();
        List<TopSuggestionItem> topSuggestions = new ArrayList<>();

        for (GrammarCorrection gc : grammars) {
            if (gc.errors() != null) {
                for (GrammarCorrection.GrammarError err : gc.errors()) {
                    String dim = classifyError(err.reason());
                    if (DIM_GRAMMAR.equals(dim)) {
                        grammarErrorCount++;
                    } else if (DIM_VOCAB.equals(dim)) {
                        vocabErrorCount++;
                    }
                    if (StringUtils.hasText(err.wrongText())) {
                        topErrors.add(new TopErrorItem(
                                err.wrongText(), err.correctText(), err.reason(), dim));
                    }
                }
            }
            if (gc.suggestion() != null) {
                String tip = gc.suggestion().tip();
                String dim = classifySuggestion(tip);
                String level = gc.suggestion().level();
                if (DIM_GRAMMAR.equals(dim)) {
                    if ("advanced".equals(level)) grammarAdvanced++;
                    else if ("better".equals(level)) grammarBetter++;
                } else if (DIM_VOCAB.equals(dim)) {
                    if ("advanced".equals(level)) vocabAdvanced++;
                    else if ("better".equals(level)) vocabBetter++;
                }
                if ((DIM_GRAMMAR.equals(dim) || DIM_VOCAB.equals(dim))
                        && StringUtils.hasText(tip)) {
                    topSuggestions.add(new TopSuggestionItem(
                            level,
                            gc.suggestion().alternatives() != null ? String.join(" / ", gc.suggestion().alternatives()) : "",
                            tip,
                            dim));
                }
            }
        }

        // ---- 分数计算 ----
        // 基础 80 分(正常水平), 错误扣分, suggestion 加分
        int grammarBase = 80 - grammarErrorCount * 5 + grammarBetter * 3 + grammarAdvanced * 6;
        int grammar = clamp(grammarBase);

        int vocabBase = 80 - vocabErrorCount * 5 + vocabBetter * 3 + vocabAdvanced * 6;
        int vocabulary = clamp(vocabBase);

        // 流利性代理: 轮数越多且错误率越低越高; 轮数过少时给 60 起评
        int totalErrors = grammarErrorCount + vocabErrorCount;
        double errorRate = userTurns == 0 ? 0 : (double) totalErrors / userTurns;
        int fluency;
        if (userTurns <= 1) {
            fluency = 55;
        } else if (userTurns <= 3) {
            fluency = clamp(70 - (int) (errorRate * 60));
        } else {
            // 轮数越多越高, 错误率越低越高
            int bonus = Math.min(15, (userTurns - 3) * 2);
            fluency = clamp(75 + bonus - (int) (errorRate * 80));
        }

        int overall = (int) new BigDecimal(grammar * 0.4 + vocabulary * 0.3 + fluency * 0.3)
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // 取前 6 条代表性错误/建议
        List<TopErrorItem> topErr = topErrors.stream().limit(6).toList();
        List<TopSuggestionItem> topSug = topSuggestions.stream().limit(6).toList();

        return new Aggregation(
                grammar, vocabulary, fluency, null, overall,
                userTurns, totalErrors, grammarBetter + grammarAdvanced + vocabBetter + vocabAdvanced,
                topErr, topSug
        );
    }

    private GrammarCorrection parse(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            // 简化: 直接用 ObjectMapper 反序列化成 Map 再手写字段, 或用 record 构造
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> errList = (List<Map<String, Object>>) map.getOrDefault("errors", List.of());
            List<GrammarCorrection.GrammarError> errors = errList.stream().map(m -> new GrammarCorrection.GrammarError(
                    (String) m.getOrDefault("wrongText", ""),
                    (String) m.getOrDefault("correctText", ""),
                    (String) m.getOrDefault("reason", "")
            )).toList();
            Map<String, Object> sMap = (Map<String, Object>) map.get("suggestion");
            GrammarCorrection.Suggestion suggestion = null;
            if (sMap != null) {
                List<String> alts = (List<String>) sMap.getOrDefault("alternatives", List.of());
                suggestion = new GrammarCorrection.Suggestion(
                        (String) sMap.getOrDefault("level", "none"),
                        (String) sMap.getOrDefault("original", ""),
                        alts,
                        (String) sMap.getOrDefault("tip", ""),
                        (String) sMap.getOrDefault("speechText", "")
                );
            }
            return new GrammarCorrection(
                    Boolean.TRUE.equals(map.get("noError")),
                    (String) map.getOrDefault("original", ""),
                    (String) map.getOrDefault("correctText", ""),
                    errors,
                    (String) map.getOrDefault("speechText", ""),
                    suggestion
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String classifyError(String reason) {
        if (!StringUtils.hasText(reason)) return DIM_GRAMMAR;
        for (KeywordRule r : ERROR_RULES) {
            for (String kw : r.keywords()) {
                if (reason.toLowerCase().contains(kw.toLowerCase())) return r.dim;
            }
        }
        return DIM_GRAMMAR; // 未识别默认语法
    }

    private String classifySuggestion(String tip) {
        if (!StringUtils.hasText(tip)) return DIM_GRAMMAR;
        String lower = tip.toLowerCase();
        // 语法关键词优先检查
        for (KeywordRule r : SUGGESTION_RULES) {
            for (String kw : r.keywords()) {
                if (tip.contains(kw) || lower.contains(kw.toLowerCase())) return r.dim;
            }
        }
        return DIM_GRAMMAR;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private record KeywordRule(List<String> keywords, String dim, String label) {}

    /** 聚合结果 */
    public record Aggregation(
            int grammarScore, int vocabularyScore, int fluencyScore, Integer pronunciationScore,
            int overallScore,
            int roundCount, int errorCount, int suggestionCount,
            List<TopErrorItem> topErrors, List<TopSuggestionItem> topSuggestions
    ) {
        public String topErrorsJson() { return toJson(topErrors); }
        public String topSuggestionsJson() { return toJson(topSuggestions); }

        private static String toJson(Object o) {
            try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
            catch (Exception e) { return "[]"; }
        }
    }

    public record TopErrorItem(String wrong, String correct, String reason, String dimension) {}
    public record TopSuggestionItem(String level, String alternatives, String tip, String dimension) {}
}
