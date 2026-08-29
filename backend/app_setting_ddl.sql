
-- 平台级配置。setting_value 保存 AES-GCM 密文，不保存明文 API Key。
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key  VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    update_by    BIGINT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_setting_update_by FOREIGN KEY (update_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
