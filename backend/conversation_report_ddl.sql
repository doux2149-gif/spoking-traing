-- 会话报告表: 每条 conversation 结束后生成一次(聚合 grammar_json + 可选 LLM 摘要)
-- 纯规则聚合四维度分数, 额外 LLM 调用仅生成自然语言总评(可异步失败)
CREATE TABLE IF NOT EXISTS conversation_report (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id   BIGINT NOT NULL UNIQUE COMMENT '关联 conversation',
    user_id           BIGINT NOT NULL,
    round_count       INT NOT NULL DEFAULT 0,
    duration          INT NOT NULL DEFAULT 0 COMMENT '秒',
    error_count       INT NOT NULL DEFAULT 0,
    suggestion_count  INT NOT NULL DEFAULT 0,

    -- 雅思四维度 0-100 分: 基于 grammar_json 规则聚合
    grammar_score     INT NOT NULL DEFAULT 0 COMMENT '语法多样性',
    vocabulary_score  INT NOT NULL DEFAULT 0 COMMENT '词汇多样性',
    fluency_score     INT NOT NULL DEFAULT 0 COMMENT '流利性与连贯性',
    pronunciation_score INT DEFAULT NULL COMMENT '发音(无 ASR 数据时为 NULL, 前端展示本次未评估)',
    overall_score     INT NOT NULL DEFAULT 0 COMMENT '加权平均(语法40%+词汇30%+流利30%)',

    -- 结构化明细(JSON): 供前端渲染 top errors / top suggestions
    top_errors        JSON NULL COMMENT '聚合后的代表性错误 [{wrong,correct,reason,dimension}]',
    top_suggestions   JSON NULL COMMENT '代表性建议 [{level,alternatives,tip,dimension}]',

    summary           TEXT NULL COMMENT 'LLM 生成的自然语言总评(200字内), @Async 可失败',
    status            TINYINT NOT NULL DEFAULT 0 COMMENT '0=待生成 1=规则聚合完成 2=已生成 LLM 摘要 3=失败',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report_user (user_id),
    INDEX idx_report_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='/chat 对话报告(规则聚合+可选 LLM 摘要)';
