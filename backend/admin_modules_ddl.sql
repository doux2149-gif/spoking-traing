-- ============================================================================
-- 管理员端四模块扩展 DDL
--   1. llm_model_price  LLM 模型单价(元/百万 token), 供用量成本统计
--   2. sys_notice       系统公告(横幅/弹窗, 生效时间窗)
--   3. sys_login_log    登录日志(成功/失败)
--   4. sys_user_online  在线会话(token jti), 支撑在线列表与强制下线
--   另: 在"系统管理"下新增 5 个菜单并授予 ROLE_ADMIN(role_id=1)
-- 幂等: 可重复执行
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. LLM 模型单价表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_model_price (
    id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    model         VARCHAR(64)   NOT NULL UNIQUE COMMENT '模型名, 与 llm_usage_log.model 对应',
    input_price   DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '输入单价(元/百万 token)',
    output_price  DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '输出单价(元/百万 token)',
    enabled       TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    remark        VARCHAR(255),
    create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 模型单价配置';

-- DeepSeek 官方价(2025): deepseek-chat 输入 0.5 元/百万 token, 输出 8 元/百万 token
INSERT INTO llm_model_price (model, input_price, output_price, enabled, remark)
SELECT 'deepseek-chat', 0.500000, 8.000000, 1, 'DeepSeek-V3 官方价(缓存未命中), 可在后台调整'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM llm_model_price WHERE model = 'deepseek-chat');

-- ---------------------------------------------------------------------------
-- 2. 系统公告表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notice (
    id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
    title          VARCHAR(200) NOT NULL COMMENT '公告标题',
    content        TEXT         NOT NULL COMMENT '公告内容(纯文本, 支持换行)',
    notice_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=通知 2=维护',
    display_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=横幅 2=弹窗',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已停用',
    publish_start  DATETIME     NULL COMMENT '生效开始时间, 空=立即',
    publish_end    DATETIME     NULL COMMENT '生效结束时间, 空=长期',
    create_by      BIGINT       NULL COMMENT '创建人 user_id',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_time (status, publish_start, publish_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告';

-- ---------------------------------------------------------------------------
-- 3. 登录日志表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL COMMENT '登录账号(登录失败时取提交值)',
    user_id     BIGINT       NULL COMMENT '用户ID, 用户不存在时为空',
    ip          VARCHAR(64)  NULL COMMENT '登录IP',
    user_agent  VARCHAR(500) NULL COMMENT 'User-Agent',
    status      TINYINT      NOT NULL COMMENT '1=成功 0=失败',
    message     VARCHAR(255) NULL COMMENT '结果消息/失败原因',
    login_time  DATETIME     NOT NULL COMMENT '登录时间',
    INDEX idx_username (username),
    INDEX idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

-- ---------------------------------------------------------------------------
-- 4. 在线会话表(以 JWT jti 为主键)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_online (
    token_id         VARCHAR(64) PRIMARY KEY COMMENT 'JWT 唯一标识(jti)',
    user_id          BIGINT      NOT NULL COMMENT '用户ID',
    username         VARCHAR(64) NOT NULL COMMENT '用户名',
    login_ip         VARCHAR(64) NULL COMMENT '登录IP',
    user_agent       VARCHAR(500) NULL COMMENT 'User-Agent',
    login_time       DATETIME    NOT NULL COMMENT '登录时间',
    last_active_time DATETIME    NOT NULL COMMENT '最后活跃时间',
    INDEX idx_user_id (user_id),
    INDEX idx_last_active (last_active_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线用户会话';

-- ---------------------------------------------------------------------------
-- 5. 管理后台菜单(挂在"系统管理"目录下, 沿用 llm_tool_ddl.sql 的幂等写法)
-- ---------------------------------------------------------------------------
-- 5.0 若还没有 "系统管理" 父菜单, 创建一个
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT 0, '系统管理', '/system', NULL, NULL, 'Setting', 'M', 10, 1, 1, NOW(), NOW()
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = '/system' AND menu_type = 'M');

-- 5.1 子菜单: 用量统计
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '用量统计', 'usage', 'system/UsageStats', 'system:usage:list', 'DataLine', 'C', 50, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'usage' AND component = 'system/UsageStats');

-- 5.2 子菜单: 公告管理
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '公告管理', 'notice', 'system/NoticeManagement', 'system:notice:list', 'BellFilled', 'C', 60, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'notice' AND component = 'system/NoticeManagement');

-- 5.3 子菜单: 登录日志
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '登录日志', 'login-log', 'system/LoginLog', 'system:loginlog:list', 'DocumentChecked', 'C', 70, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'login-log' AND component = 'system/LoginLog');

-- 5.4 子菜单: 在线用户
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '在线用户', 'online', 'system/OnlineUser', 'system:online:list', 'Connection', 'C', 80, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'online' AND component = 'system/OnlineUser');

-- 5.5 子菜单: 菜单管理
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '菜单管理', 'menu', 'system/MenuManagement', 'system:menu:list', 'Menu', 'C', 90, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'menu' AND component = 'system/MenuManagement');

-- 5.6 为 ROLE_ADMIN(role_id=1) 补齐授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.component IN ('system/UsageStats', 'system/NoticeManagement', 'system/LoginLog', 'system/OnlineUser', 'system/MenuManagement')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
