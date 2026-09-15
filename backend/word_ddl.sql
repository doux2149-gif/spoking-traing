-- 单词表: 支持管理员手动录入 + DeepSeek 批量生成, 按 category/difficulty 分类, is_published 控制用户端可见
CREATE TABLE IF NOT EXISTS `word` (
  `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  `english`             VARCHAR(100) NOT NULL UNIQUE COMMENT '英文单词, UNIQUE 防重复',
  `chinese`             VARCHAR(200) NOT NULL COMMENT '中文释义',
  `phonetic`            VARCHAR(100) NULL COMMENT '音标, LLM 生成或手动填',
  `part_of_speech`      VARCHAR(50) NULL COMMENT '词性: noun/verb/adjective/adverb',
  `category`            VARCHAR(50) DEFAULT '日常' COMMENT '类别: IELTS5/IELTS6/日常/商务/CET4/CET6/考研',
  `difficulty`          TINYINT DEFAULT 0 COMMENT '难度 0-5',
  `example_sentence`    TEXT NULL COMMENT '例句(英文)',
  `example_translation` TEXT NULL COMMENT '例句翻译(中文)',
  `source`              VARCHAR(20) DEFAULT 'UPLOAD' COMMENT 'UPLOAD 手动录入 / GENERATED AI 生成',
  `is_published`        TINYINT DEFAULT 1 COMMENT '1=用户端可见 0=仅管理端可见',
  `create_time`         DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (`category`),
  INDEX idx_published (`is_published`),
  INDEX idx_source (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单词表(手动录入 + AI 生成)';
