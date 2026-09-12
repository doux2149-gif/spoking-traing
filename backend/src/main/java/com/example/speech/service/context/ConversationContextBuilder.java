package com.example.speech.service.context;

import com.example.speech.config.DeepSeekProperties;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 对话上下文构建器
 *
 * 分层架构 (自顶向下):
 *  Layer 1: System Prompt (固定,场景模式 or 通用纠错模式 + 长期记忆注入)
 *  Layer 2: 上下文压缩摘要 (若存在 context_summary,插入为 user+assistant 确认对)
 *  Layer 3: 最近 N 轮对话 (滑动窗口,默认 10 轮 = 20 条)
 *
 *  同时暴露 token 估算与是否"超过阈值需要生成新摘要"的判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationContextBuilder {

    /** 最近保留多少轮 (user+assistant 为 1 轮) */
    private static final int RECENT_ROUNDS = 10;
    /** 单条消息平均 token 估算(中/英混合保守估计) */
    private static final int AVG_TOKENS_PER_CHAR_DIVISOR = 4;
    /** 超过模型 max_tokens 的这个比例时触发"需要摘要压缩"判断 */
    private static final double SUMMARY_TRIGGER_RATIO = 0.7;
    /** 摘要压缩的触发前提:至少超过多少轮才需要做摘要 (避免太短就压缩) */
    private static final int SUMMARY_MIN_ROUNDS_TRIGGER = 15;

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    /** 英文通用纠错 System Prompt (从 ChatController 抽取,保持一致) */
    private static final String ENGLISH_SYSTEM_PROMPT = """
            你是一名雅思口语考试考官,和用户进行雅思口语练习对话。

            雅思口语评分标准(用于判断建议):
            - 词汇多样性(Lexical Resource):是否使用了高级词汇、习语搭配、同义替换
            - 语法多样性及准确性(Grammatical Range):是否使用了复合句、条件句、虚拟语气等多样语法结构
            - 流利性与连贯性(Fluency and Coherence):是否使用了连接词、话语标记语
            用户的表达如果只用了基础词汇和简单句型,即使语法正确,也可以建议更符合雅思高分标准的表达。

            规则:
            1. 首先输出正常的英文聊天回复,口语化,句子简短,不要输出中文。
            2. 每次回复完成后,都必须另起一行,用 ##GRAMMAR_JSON## 和 ##END_JSON## 包裹纠错 JSON。无论之前是否纠错过,每一轮回复后都要输出,不能省略。
            3. 只针对用户本次输入的英文句子纠错,不要修改 AI 自己的回复。
            4. 如果用户输入没有语法、拼写、句式错误,noError 为 true。
            5. 如果存在错误,noError 为 false,填写:
               - original:用户原始句子
               - correctText:修正后的完整句子
               - errors:错误数组,每项含 wrongText(错误片段)、correctText(修改片段)、reason(中文简短错误原因)
               - speechText:适合朗读的中英文结合口语化文本,直接告诉用户哪里错了、正确的怎么说。不超过 100 字。
            6. 如果用户输入没有语法错误(noError 为 true),必须给出 suggestion 建议。即使用户表达已经很好,也要提供另一种更丰富或更多样的表达方式,帮助用户精益求精:
               - level:"better"(表达可以提升,符合雅思7分水平) 或 "advanced"(表达已经不错,提供雅思8分水平的进阶说法)
               - original:用户原句
               - alternatives:2-3种替代表达(从不同角度:更地道/更高级/更多样)
               - tip:中文简短解释为什么这样更好,对应雅思哪个评分维度
               - speechText:中英文结合口语化朗读文本,不超过100字
            7. suggestion 和纠错互斥:有语法错误时不给 suggestion(level="none");无语法错误时必须给 suggestion(level 为 "better" 或 "advanced"),不能为 "none"。
            8. JSON 严格合法,不要加多余注释、不要额外文字,只允许在标记符号中间放 JSON。
            9. ##END_JSON## 之后不要输出任何内容。

            重要:每一轮对话结束后都必须输出纠错 JSON,包括多轮对话的每一轮。历史对话中 AI 的回复也都带有纠错 JSON,请保持这个格式。
            注意:只要没有语法错误,就必须给建议。即使用户表达已经很好,也要给出另一种说法,帮助用户积累更多表达方式。

            工具使用规则:
            - 当用户询问实时信息(如今天星期几、当前时间、天气、外部数据等模型无法直接知道的事实)时,如果提供了相关工具(tools 数组),必须调用工具获取准确信息,不要凭记忆猜测或拒绝回答。
            - 调用工具后,把工具返回的结果自然地融入英文回复中,继续推进口语对话。
            - 工具调用是获取事实的途径,不影响你作为口语考官的角色。

            输出示例(有错):
            I'm pretty good, thank you for asking!
            ##GRAMMAR_JSON##
            {"noError":false,"original":"how is you","correctText":"how are you","errors":[{"wrongText":"is","correctText":"are","reason":"主谓一致,you 后面 be 动词需要用 are"}],"speechText":"你刚才说的 how is you 里,is 用得不太对。你应该说 how are you,因为 you 后面要用 are。","suggestion":{"level":"none","original":"","alternatives":[],"tip":"","speechText":""}}
            ##END_JSON##

            输出示例(正确,可以提升):
            I am fine, nice to talk with you.
            ##GRAMMAR_JSON##
            {"noError":true,"original":"","correctText":"","errors":[],"speechText":"","suggestion":{"level":"better","original":"I am fine","alternatives":["I'm doing quite well, thank you for asking.","I've been pretty good, thanks!","Can't complain, everything's going well."],"tip":"I am fine 词汇基础,雅思口语建议用更自然的缩写和话语标记语,展示词汇多样性。","speechText":"你说得没错！不过在雅思口语中,更自然的说法是 I'm doing quite well, thank you for asking,展示了词汇多样性。"}}
            ##END_JSON##

            输出示例(正确且表达优秀,仍给进阶建议):
            I've been doing quite well, thank you for asking. I recently started a new project that's been keeping me on my toes.
            ##GRAMMAR_JSON##
            {"noError":true,"original":"","correctText":"","errors":[],"speechText":"","suggestion":{"level":"advanced","original":"I've been doing quite well, thank you for asking.","alternatives":["I'm doing great, thanks for asking. I've recently embarked on a new venture that's been quite exhilarating.","Everything's been going swimmingly, thanks! I just kicked off a new project that's really keeping me engaged."],"tip":"表达已经很好,用了习语 keeping me on my toes。进阶建议:用 embark on 替换 start,用 exhilarating 替换 exciting,展示8分词汇多样性。","speechText":"你说得非常好！如果想更进一步,可以说 I've recently embarked on a new venture that's been quite exhilarating,embark on 和 exhilarating 都是雅思8分词汇。"}}
            ##END_JSON##
            """;

    private static final String SCENE_SYSTEM_PROMPT_TEMPLATE = """
            你是一名雅思口语考试考官,正在进行雅思口语场景对话练习。

            雅思口语评分标准(用于判断建议):
            - 词汇多样性(Lexical Resource):是否使用了高级词汇、习语搭配、同义替换
            - 语法多样性及准确性(Grammatical Range):是否使用了复合句、条件句、虚拟语气等多样语法结构
            - 流利性与连贯性(Fluency and Coherence):是否使用了连接词、话语标记语
            用户的表达如果只用了基础词汇和简单句型,即使语法正确,也可以建议更符合雅思高分标准的表达。

            场景设定:
            %s

            规则:
            1. 始终保持角色设定,不要跳出场景。用自然口语化的英语回复,每次不超过3句。
            2. 根据用户回复推进对话,如果用户表达困难可适当提示或引导。
            3. 每次回复完成后,都必须另起一行,用 ##GRAMMAR_JSON## 和 ##END_JSON## 包裹纠错 JSON。每一轮都要输出,不能省略。
            4. 只针对用户本次输入的英文句子纠错。
            5. 如果没有语法错误,noError 为 true。
            6. 如果有错误,noError 为 false,填写 original、correctText、errors(含 wrongText/correctText/reason)、speechText(中英文结合口语化解释,不超过100字)。
            7. 如果用户输入没有语法错误(noError 为 true),必须给出 suggestion 建议。即使用户表达已经很好,也要提供另一种更丰富或更多样的表达方式,帮助用户精益求精:
               - level:"better"(表达可以提升,符合雅思7分水平) 或 "advanced"(表达已经不错,提供雅思8分水平的进阶说法)
               - original:用户原句
               - alternatives:2-3种替代表达(从不同角度:更地道/更高级/更多样)
               - tip:中文简短解释为什么这样更好,对应雅思哪个评分维度
               - speechText:中英文结合口语化朗读文本,不超过100字
            8. suggestion 和纠错互斥:有语法错误时不给 suggestion(level="none");无语法错误时必须给 suggestion(level 为 "better" 或 "advanced"),不能为 "none"。
            9. JSON 严格合法,##END_JSON## 之后不要输出任何内容。

            注意:只要没有语法错误,就必须给建议。即使用户表达已经很好,也要给出另一种说法,帮助用户积累更多表达方式。
            """;

    /** 长期记忆注入模板 (追加到 system prompt 末尾) */
    private static final String LONG_TERM_MEMORY_APPEND = """

            [学习记忆(仅供参考,不要直接读出)]
            %s
            [/学习记忆]
            """;

    /**
     * 构造用于生成上下文摘要的 prompt (对旧对话做压缩)
     */
    private static final String CONTEXT_SUMMARY_PROMPT = """
            你是一个对话摘要助手。请用一段 200 字以内的中文总结下面这段英语口语练习对话。
            要求包含:对话主题、用户讨论的主要内容、用户暴露的典型语法/表达问题(如有)。
            只输出摘要文本,不要加解释或多余内容。
            """;

    /**
     * 构建最终发给 LLM 的 messages 列表
     *
     * @param enableGrammar  true=启用语法纠错/建议 system prompt
     * @param scenePrompt    场景 prompt (空=通用模式)
     * @param contextSummary 历史压缩摘要 (可为空)
     * @param userPrefsJson  用户长期记忆 preferences_json (可为空)
     * @param messages       完整对话消息 (按时间升序排列)
     * @return ContextBuildResult: messages + 元数据 (是否截断/是否需压缩摘要)
     */
    public ContextBuildResult build(
            boolean enableGrammar,
            String scenePrompt,
            String contextSummary,
            String userPrefsJson,
            List<ChatMessage> messages
    ) {
        // Layer 1: System Prompt
        List<ChatMessage> result = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(enableGrammar, scenePrompt, userPrefsJson);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            result.add(new ChatMessage("system", systemPrompt));
        }

        // 非 system 消息(按顺序,去除已有 system,保留原始顺序)
        List<ChatMessage> nonSystem = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (!"system".equals(m.role())) {
                nonSystem.add(m);
            }
        }

        // Layer 2: 压缩摘要 (若存在,且总消息超过窗口)
        boolean truncated = false;
        int maxRecentMsgs = RECENT_ROUNDS * 2;
        if (nonSystem.size() > maxRecentMsgs && StringUtils.hasText(contextSummary)) {
            result.add(new ChatMessage("user", "之前的对话摘要如下,请参考:\n" + contextSummary));
            result.add(new ChatMessage("assistant", "好的,我会参考之前的对话摘要,结合最近的对话继续。"));
        }

        // Layer 3: 最近 N 条消息
        if (nonSystem.size() > maxRecentMsgs) {
            truncated = true;
            int from = nonSystem.size() - maxRecentMsgs;
            result.addAll(nonSystem.subList(from, nonSystem.size()));
        } else {
            result.addAll(nonSystem);
        }

        // 判断是否"超过阈值需要生成新摘要"
        int estimatedTokens = estimateTokens(result);
        int modelMax = properties.maxTokens();
        int estimatedWindowWithoutSummary = estimateWindowWithoutSummary(systemPrompt, nonSystem);
        boolean shouldSummarize = truncated
                && nonSystem.size() >= SUMMARY_MIN_ROUNDS_TRIGGER * 2
                && estimatedWindowWithoutSummary >= modelMax * SUMMARY_TRIGGER_RATIO;

        // 若窗口太大且还没摘要,提示也需要摘要
        if (!shouldSummarize && estimatedTokens >= modelMax * SUMMARY_TRIGGER_RATIO
                && nonSystem.size() >= SUMMARY_MIN_ROUNDS_TRIGGER * 2) {
            shouldSummarize = true;
        }

        return new ContextBuildResult(result, truncated, estimatedTokens, shouldSummarize);
    }

    /** 构建 system prompt (场景/通用模式选择 + 长期记忆注入) */
    private String buildSystemPrompt(boolean enableGrammar, String scenePrompt, String userPrefsJson) {
        if (!enableGrammar) {
            // 不启用纠错:如果有长期记忆仍注入到一个简短 system prompt
            String memory = buildLongTermMemoryText(userPrefsJson);
            if (memory == null) return null;
            return "你是一个友善的英语口语陪练。\n" + memory;
        }
        String base;
        if (StringUtils.hasText(scenePrompt)) {
            base = String.format(SCENE_SYSTEM_PROMPT_TEMPLATE, scenePrompt);
        } else {
            base = ENGLISH_SYSTEM_PROMPT;
        }
        String memory = buildLongTermMemoryText(userPrefsJson);
        if (memory == null) return base;
        return base + String.format(LONG_TERM_MEMORY_APPEND, memory);
    }

    /** 从 preferences_json 抽取出可注入的长期记忆文本 */
    private String buildLongTermMemoryText(String userPrefsJson) {
        if (!StringUtils.hasText(userPrefsJson)) return null;
        try {
            JsonNode node = objectMapper.readTree(userPrefsJson);
            StringBuilder sb = new StringBuilder();
            if (node.has("nickname") && StringUtils.hasText(node.path("nickname").asText())) {
                sb.append("用户昵称: ").append(node.path("nickname").asText()).append("。\n");
            }
            if (node.has("goalScore") && StringUtils.hasText(node.path("goalScore").asText())) {
                sb.append("用户的目标雅思口语分数: ").append(node.path("goalScore").asText()).append("。\n");
            }
            JsonNode frequentErrors = node.path("frequentErrors");
            if (frequentErrors.isArray() && !frequentErrors.isEmpty()) {
                sb.append("用户历史高频语法错误(请重点关注并提醒): ");
                List<String> parts = new ArrayList<>();
                for (JsonNode err : frequentErrors) {
                    String type = err.path("type").asText("");
                    int count = err.path("count").asInt(0);
                    if (!type.isEmpty()) {
                        parts.add(mapErrorTypeToCn(type) + "(出现" + count + "次)");
                    }
                }
                if (!parts.isEmpty()) sb.append(String.join("、", parts)).append("。\n");
            }
            JsonNode weakScenes = node.path("weakScenes");
            if (weakScenes.isArray() && !weakScenes.isEmpty()) {
                sb.append("用户薄弱场景(如果对话涉及,请多练习): ");
                List<String> parts = new ArrayList<>();
                for (JsonNode s : weakScenes) {
                    String sc = s.asText("");
                    if (!sc.isEmpty()) parts.add(sc);
                }
                if (!parts.isEmpty()) sb.append(String.join("、", parts)).append("。\n");
            }
            return sb.length() == 0 ? null : sb.toString().trim();
        } catch (Exception e) {
            log.warn("解析 preferences_json 失败,忽略长期记忆注入: {}", e.getMessage());
            return null;
        }
    }

    private static final Pattern ERROR_TYPE_PATTERN = Pattern.compile("^\\{\"type\":\"([^\"]+)\",\"count\":(\\d+)\\}$");

    private String mapErrorTypeToCn(String type) {
        if (type == null) return "未知错误";
        return switch (type) {
            case "tense_past" -> "过去时态";
            case "tense_present" -> "现在时态";
            case "tense_perfect" -> "完成时态";
            case "article" -> "冠词(a/an/the)";
            case "preposition" -> "介词";
            case "subject_verb" -> "主谓一致";
            case "plural" -> "名词单复数";
            case "word_order" -> "语序";
            case "vocabulary" -> "词汇搭配";
            default -> type;
        };
    }

    /** 粗略估算 tokens (字符数 / 4,中英文混合保守) */
    public int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage m : messages) {
            if (m.content() != null) {
                total += Math.max(1, m.content().length() / AVG_TOKENS_PER_CHAR_DIVISOR);
            }
        }
        return total;
    }

    /** 估算如果不用摘要、直接放 system + 全部消息的 token 量 (用于判断是否触发摘要) */
    private int estimateWindowWithoutSummary(String systemPrompt, List<ChatMessage> nonSystem) {
        int total = 0;
        if (systemPrompt != null) total += systemPrompt.length() / AVG_TOKENS_PER_CHAR_DIVISOR;
        int maxRecent = RECENT_ROUNDS * 2;
        int from = Math.max(0, nonSystem.size() - maxRecent);
        for (int i = from; i < nonSystem.size(); i++) {
            ChatMessage m = nonSystem.get(i);
            if (m.content() != null) total += m.content().length() / AVG_TOKENS_PER_CHAR_DIVISOR;
        }
        return total;
    }

    /** 构建用于生成"上下文压缩摘要"的 LLM 请求 messages —— 对要被挤出窗口的部分做摘要 */
    public List<ChatMessage> buildSummaryPromptMessages(List<ChatMessage> allNonSystem) {
        int maxRecent = RECENT_ROUNDS * 2;
        if (allNonSystem.size() <= maxRecent) {
            // 没超过窗口,无需摘要
            return null;
        }
        int cutIndex = allNonSystem.size() - maxRecent;
        // 对齐到偶数 (避免把 user/assistant 对拆开)
        if (cutIndex % 2 != 0) cutIndex--;
        if (cutIndex <= 0) return null;
        List<ChatMessage> toSummarize = allNonSystem.subList(0, cutIndex);
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : toSummarize) {
            String role = "user".equals(m.role()) ? "用户" : "考官";
            sb.append(role).append(": ").append(stripGrammarJson(m.content())).append("\n");
        }
        List<ChatMessage> result = new ArrayList<>();
        result.add(new ChatMessage("system", CONTEXT_SUMMARY_PROMPT));
        result.add(new ChatMessage("user", sb.toString()));
        return result;
    }

    /** 从内容中剥离 ##GRAMMAR_JSON##...##END_JSON## 块,避免摘要里塞进 JSON */
    private static final Pattern GRAMMAR_JSON_PATTERN =
            Pattern.compile("##GRAMMAR_JSON##.*?##END_JSON##", Pattern.DOTALL);

    public static String stripGrammarJson(String content) {
        if (content == null) return "";
        Matcher m = GRAMMAR_JSON_PATTERN.matcher(content);
        return m.replaceAll("").trim();
    }

    /**
     * 构建结果
     */
    public record ContextBuildResult(
            List<ChatMessage> messages,
            boolean truncated,
            int estimatedTokens,
            boolean shouldSummarize
    ) {}
}
