package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.Conversation;
import com.example.speech.entity.ConversationMessage;
import com.example.speech.entity.ConversationReport;
import com.example.speech.mapper.ConversationReportMapper;
import com.example.speech.service.context.ConversationContextBuilder;
import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话报告: 规则聚合 + 可选 @Async LLM 摘要。
 * 同一条 conversation 只生成一份报告, conversation_id UNIQUE 避免重复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationReportService {

    private final ConversationReportMapper reportMapper;
    private final ConversationService conversationService;
    private final GrammarReportService grammarReportService;
    private final DeepSeekClient deepSeekClient;

    /**
     * 结束会话时同步触发: 规则聚合落库(status=1), 立即返回给前端;
     * 再异步触发 LLM 摘要更新 summary + status=2; 失败保持 1, 不阻塞主流程。
     */
    @Transactional
    public ConversationReport generateOnEnd(Conversation conversation) {
        // 幂等: 已经有报告就直接返回
        ConversationReport existing = reportMapper.selectOne(
                new LambdaQueryWrapper<ConversationReport>().eq(ConversationReport::getConversationId, conversation.getId()));
        if (existing != null) {
            return existing;
        }
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversation.getId());
        GrammarReportService.Aggregation agg = grammarReportService.aggregate(messages);

        ConversationReport report = new ConversationReport();
        report.setConversationId(conversation.getId());
        report.setUserId(conversation.getUserId());
        report.setRoundCount(firstNonNull(conversation.getRoundCount(), agg.roundCount()));
        report.setDuration(firstNonNull(conversation.getDuration(), 0));
        report.setErrorCount(firstNonNull(conversation.getErrorCount(), agg.errorCount()));
        report.setSuggestionCount(firstNonNull(conversation.getSuggestionCount(), agg.suggestionCount()));
        report.setGrammarScore(agg.grammarScore());
        report.setVocabularyScore(agg.vocabularyScore());
        report.setFluencyScore(agg.fluencyScore());
        report.setPronunciationScore(null);
        report.setOverallScore(agg.overallScore());
        report.setTopErrors(agg.topErrorsJson());
        report.setTopSuggestions(agg.topSuggestionsJson());
        report.setStatus(1);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        reportMapper.insert(report);

        // 异步 LLM 摘要
        try {
            generateSummaryAsync(report.getId());
        } catch (Exception _e) {
            // 调用方可能已关闭 context, 忽略
        }
        return report;
    }

    /** 前端主动重新生成(规则部分已稳定; 仅重跑 LLM 摘要) */
    @Transactional
    public ConversationReport regenerateSummary(Long reportId) {
        ConversationReport report = reportMapper.selectById(reportId);
        if (report == null) throw new IllegalArgumentException("报告不存在");
        report.setStatus(1);
        reportMapper.updateById(report);
        generateSummaryAsync(reportId);
        return report;
    }

    @Async
    public void generateSummaryAsync(Long reportId) {
        try {
            ConversationReport report = reportMapper.selectById(reportId);
            if (report == null) return;
            // 拉一次全量对话做摘要文本
            List<ConversationMessage> messages = conversationService.getConversationMessages(report.getConversationId());
            StringBuilder sb = new StringBuilder();
            for (ConversationMessage m : messages) {
                if ("assistant".equals(m.getRole())) continue;
                sb.append("用户: ").append(ConversationContextBuilder.stripGrammarJson(m.getContent())).append('\n');
            }
            if (sb.isEmpty()) {
                markFailed(reportId, "用户对话为空,无法生成摘要");
                return;
            }

            List<ChatMessage> prompt = new ArrayList<>();
            prompt.add(new ChatMessage("system", """
                    你是雅思口语考官风格的中文点评助手。根据下面的对话与四维度分数,
                    用 150-200 字给出自然语言总结, 包含:
                    1) 整体表现一句话
                    2) 各维度亮点/短板
                    3) 1-2 条具体改进建议
                    不要重复分数数字, 用"约7分水平"这类表达。
                    """));
            prompt.add(new ChatMessage("user", String.format("""
                    四维度分数(0-100):
                    语法多样性: %d
                    词汇多样性: %d
                    流利性与连贯性: %d
                    总分: %d
                    总轮数: %d  错误数: %d  建议数: %d
                    
                    代表性错误/建议(供你参考具体问题):
                    %s
                    
                    用户的英文对话:
                    %s
                    """,
                    report.getGrammarScore(), report.getVocabularyScore(), report.getFluencyScore(),
                    report.getOverallScore(), report.getRoundCount(), report.getErrorCount(), report.getSuggestionCount(),
                    report.getTopErrors() == null ? "（无）" : report.getTopErrors(),
                    sb
            )));

            String summary = deepSeekClient.chat(prompt);
            if (summary != null && !summary.isBlank()) {
                ConversationReport update = new ConversationReport();
                update.setId(reportId);
                update.setSummary(summary.trim());
                update.setStatus(2);
                update.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(update);
            } else {
                markFailed(reportId, "LLM 返回空");
            }
        } catch (Exception e) {
            log.warn("LLM 摘要生成失败(reportId={}): {}", reportId, e.getMessage());
            markFailed(reportId, e.getMessage());
        }
    }

    private void markFailed(Long reportId, String reason) {
        try {
            ConversationReport update = new ConversationReport();
            update.setId(reportId);
            update.setSummary("摘要生成失败: " + reason);
            update.setStatus(3);
            update.setUpdateTime(LocalDateTime.now());
            reportMapper.updateById(update);
        } catch (Exception ignore) {}
    }

    public ConversationReport getByConversation(Long conversationId, Long userId) {
        ConversationReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<ConversationReport>().eq(ConversationReport::getConversationId, conversationId));
        if (report != null && !report.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问他人报告");
        }
        return report;
    }

    public List<ConversationReport> getByUser(Long userId) {
        return reportMapper.selectList(
                new LambdaQueryWrapper<ConversationReport>()
                        .eq(ConversationReport::getUserId, userId)
                        .orderByDesc(ConversationReport::getCreateTime));
    }

    private static int firstNonNull(Integer a, int b) { return a != null ? a : b; }
}
