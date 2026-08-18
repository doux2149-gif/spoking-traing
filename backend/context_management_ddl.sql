-- 上下文管理新增表与字段 (MySQL 不支持 ADD COLUMN IF NOT EXISTS,使用 information_schema 规避)

-- 1. sys_user 表:新增长期记忆/偏好 JSON
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'preferences_json');
SET @sqlstmt := IF(@exist = 0,
    'ALTER TABLE sys_user ADD COLUMN preferences_json TEXT NULL COMMENT ''用户偏好与长期记忆JSON(高频错误/薄弱场景/设置项)''',
    'SELECT ''preferences_json already exists''');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. conversation 表:新增上下文压缩摘要(与学习summary区分)
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversation' AND COLUMN_NAME = 'context_summary');
SET @sqlstmt := IF(@exist = 0,
    'ALTER TABLE conversation ADD COLUMN context_summary TEXT NULL COMMENT ''LLM压缩的上下文摘要(上下文管理用)''',
    'SELECT ''context_summary already exists''');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. LLM token 用量日志
CREATE TABLE IF NOT EXISTS llm_usage_log (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL COMMENT '用户ID',
    conversation_id    BIGINT DEFAULT NULL COMMENT '会话ID(非对话场景为空)',
    model              VARCHAR(100) NOT NULL COMMENT '使用的模型名',
    prompt_tokens      INT DEFAULT 0 COMMENT '输入 tokens',
    completion_tokens  INT DEFAULT 0 COMMENT '输出 tokens',
    total_tokens       INT DEFAULT 0 COMMENT '合计 tokens',
    operation          VARCHAR(50) DEFAULT 'chat' COMMENT '操作类型 chat/summary/grammar/other',
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_usage_user (user_id),
    INDEX idx_usage_conv (conversation_id),
    INDEX idx_usage_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM API token用量日志';
