-- ============================================================================
-- LLM 工具动态注册机制 DDL
-- 工具来源:
--   - SPRING : 代码级工具(实现 LlmTool 接口的 @Component), 不存于此表
--   - HTTP   : 声明式 HTTP 工具, 调用外部 endpoint 获取结果
--   - SCRIPT : 脚本工具(预留, 暂不支持)
-- 管理页面可对 HTTP/SCRIPT 类型工具增删改查; SPRING 类型由代码维护。
-- ============================================================================

CREATE TABLE IF NOT EXISTS sys_llm_tool (
    id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
    tool_name        VARCHAR(64)  NOT NULL UNIQUE COMMENT '工具名(供模型调用), 小写下划线',
    description      VARCHAR(500) NOT NULL          COMMENT '工具描述, 告诉模型什么场景调用',
    parameters_schema TEXT                          COMMENT '参数 JSON Schema, 可空(无参工具)',
    tool_type        VARCHAR(16)  NOT NULL DEFAULT 'HTTP' COMMENT 'HTTP / SCRIPT',
    http_endpoint    VARCHAR(500)                  COMMENT 'HTTP 工具的调用地址',
    http_method      VARCHAR(8)   NOT NULL DEFAULT 'POST',
    http_headers     TEXT                          COMMENT '额外请求头, JSON 对象',
    timeout_ms       INT          NOT NULL DEFAULT 5000,
    enabled          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    version          BIGINT       NOT NULL DEFAULT 0 COMMENT '版本号, 每次修改+1, 用于动态感知',
    remark           VARCHAR(255),
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tool_type (tool_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 动态工具注册表';

-- ============================================================================
-- 管理后台菜单: 工具管理
--   父菜单: 系统管理 (parentId 需与现有 系统管理 菜单 id 对齐, 这里用子菜单方式)
--   若已存在则跳过(IF NOT EXISTS 兼容)
-- ============================================================================
-- 注意: 系统管理菜单 id 在不同环境可能不同, 这里采用 "按路径 upsert" 的方式,
-- 先查 parent id, 再插入子菜单。若使用 MySQL 不支持 upsert, 可手动按现有 系统管理 id 调整。

-- 1) 若还没有 "系统管理" 父菜单, 创建一个
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT 0, '系统管理', '/system', NULL, NULL, 'Setting', 'M', 10, 1, 1, NOW(), NOW()
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = '/system' AND menu_type = 'M');

-- 2) 在 "系统管理" 下新增 "工具管理" 子菜单 (若已存在则跳过)
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible, is_internal, create_time, update_time)
SELECT m.id, '工具管理', 'tool', 'system/ToolManagement', 'system:tool:list', 'Tools', 'C', 40, 1, 1, NOW(), NOW()
FROM sys_menu m
WHERE m.parent_id = 0 AND m.path = '/system' AND m.menu_type = 'M'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = 'tool' AND component = 'system/ToolManagement');

-- 3) 为所有角色授予该菜单 (避免权限缺失导致菜单不可见)
-- 注意: 如果使用 mybatis-plus 的 sys_role_menu 表, 需要为每个角色补齐关联
-- 这里仅给 ROLE_ADMIN(通常 role_id=1) 授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.path = 'tool' AND m.component = 'system/ToolManagement'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );
