package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话报告: /chat 对话结束后基于 grammar_json 规则聚合 + 可选 LLM 摘要生成。
 * 同一条 conversation 仅一份报告, conversation_id UNIQUE。
 */
@Data
@TableName("conversation_report")
public class ConversationReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("conversation_id")
    private Long conversationId;
    @TableField("user_id")
    private Long userId;
    @TableField("round_count")
    private Integer roundCount;
    private Integer duration;
    @TableField("error_count")
    private Integer errorCount;
    @TableField("suggestion_count")
    private Integer suggestionCount;

    /** 雅思四维度 0-100 */
    @TableField("grammar_score")
    private Integer grammarScore;
    @TableField("vocabulary_score")
    private Integer vocabularyScore;
    @TableField("fluency_score")
    private Integer fluencyScore;
    /** 发音无 ASR 数据, 为 null */
    @TableField("pronunciation_score")
    private Integer pronunciationScore;
    @TableField("overall_score")
    private Integer overallScore;

    /** JSON 文本, 保留具体明细给前端 */
    @TableField("top_errors")
    private String topErrors;
    @TableField("top_suggestions")
    private String topSuggestions;

    private String summary;

    /** 0=待生成 1=规则聚合完成 2=已生成 LLM 摘要 3=失败 */
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
