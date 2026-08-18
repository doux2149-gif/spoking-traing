CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(100) NOT NULL COMMENT '密码',
  nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  avatar VARCHAR(255) DEFAULT NULL COMMENT '头像',
  email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  status TINYINT DEFAULT 1 COMMENT '状态 1正常 0停用',
  role_id BIGINT DEFAULT NULL COMMENT '角色ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_user_username (username),
  INDEX idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
  role_key VARCHAR(50) NOT NULL UNIQUE COMMENT '角色权限字符',
  description VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  sort INT DEFAULT 0 COMMENT '排序',
  status TINYINT DEFAULT 1 COMMENT '状态 1正常 0停用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
  parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
  menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
  path VARCHAR(100) DEFAULT NULL COMMENT '路由路径',
  component VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
  perms VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
  icon VARCHAR(100) DEFAULT NULL COMMENT '图标',
  menu_type CHAR(1) DEFAULT 'M' COMMENT '类型 M目录 C菜单 F按钮',
  sort INT DEFAULT 0 COMMENT '排序',
  visible TINYINT DEFAULT 1 COMMENT '状态 1显示 0隐藏',
  is_internal TINYINT DEFAULT 0 COMMENT '是否为系统内置 1是 0否',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  INDEX idx_role_menu_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS transcription_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_filename VARCHAR(255) NOT NULL,
  language VARCHAR(32) NULL,
  audio_encoding VARCHAR(16) NOT NULL,
  sample_rate INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  result_text LONGTEXT NULL,
  error_message VARCHAR(1000) NULL,
  sid VARCHAR(128) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_transcription_created_at (created_at),
  INDEX idx_transcription_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_role (role_name, role_key, description, sort) VALUES
('超级管理员', 'admin', '系统超级管理员', 1),
('普通用户', 'user', '普通用户', 2);

INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible) VALUES
(0, '系统管理', '/system', NULL, NULL, 'setting', 'M', 1, 1),
(1, '用户管理', 'user', 'system/user', 'system:user:list', 'user', 'C', 1, 1),
(1, '角色管理', 'role', 'system/role', 'system:role:list', 'team', 'C', 2, 1),
(0, '业务管理', '/business', NULL, NULL, 'appstore', 'M', 2, 1),
(4, '语音对话', 'chat', 'chat/index', 'business:chat', 'wechat', 'C', 1, 1);

INSERT INTO sys_user (username, password, nickname, email, status, role_id) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 'admin@example.com', 1, 1);

-- 场景口语练习表
CREATE TABLE IF NOT EXISTS speaking_scene (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '场景ID',
  name          VARCHAR(100) NOT NULL COMMENT '场景名称',
  description   TEXT COMMENT '场景描述',
  ai_role       VARCHAR(100) COMMENT 'AI扮演角色',
  difficulty    TINYINT DEFAULT 1 COMMENT '难度 1入门 2初级 3中级 4高级',
  opening_line  TEXT COMMENT 'AI开场白',
  system_prompt TEXT COMMENT '注入LLM的场景指令',
  icon          VARCHAR(50) COMMENT '场景图标',
  sort          INT DEFAULT 0 COMMENT '排序',
  status        TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='口语练习场景表';

-- 练习记录表
CREATE TABLE IF NOT EXISTS practice_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  scene_id    BIGINT NOT NULL COMMENT '场景ID',
  scene_name  VARCHAR(100) COMMENT '场景名称(冗余)',
  rounds      INT DEFAULT 0 COMMENT '对话轮数',
  duration    INT DEFAULT 0 COMMENT '练习时长(秒)',
  error_count INT DEFAULT 0 COMMENT '语法错误数',
  score       INT COMMENT '练习评分(0-100)',
  summary     TEXT COMMENT 'AI生成的练习总结',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_practice_user (user_id),
  INDEX idx_practice_scene (scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='口语练习记录表';

-- 预设场景数据
INSERT INTO speaking_scene (name, description, ai_role, difficulty, opening_line, system_prompt, icon, sort, status) VALUES
('餐厅点餐', '在餐厅与服务员对话，完成从入座到点餐到买单的全过程', '餐厅服务员', 1,
'Welcome to our restaurant! How many people are in your party today?',
'你是餐厅服务员，用户是顾客。引导用户完成入座、点餐、加菜、买单等流程。菜单上有牛排、沙拉、意面、咖啡等。注意礼貌用语。',
'Food', 1, 1),
('机场值机', '在机场柜台办理值机手续，包括确认航班、选座、托运行李', '机场地勤人员', 2,
'Good morning! May I see your passport and ticket, please?',
'你是机场值机柜台地勤人员，用户是乘客。帮用户办理值机、选座位、托运行李。航班号UA1234，目的地纽约。',
'Plane', 2, 1),
('酒店入住', '在酒店前台办理入住手续，包括确认预订、询问设施等', '酒店前台接待', 2,
'Welcome to Grand Hotel! Do you have a reservation with us?',
'你是酒店前台接待员，用户是住客。帮用户办理入住、介绍房间设施和早餐时间。用户预订了豪华大床房，住2晚。',
'House', 3, 1),
('购物退货', '在商店退换一件有问题的商品，与店员沟通', '商店店员', 3,
'Hello! How can I help you today?',
'你是商店店员，用户是顾客。用户要退换一件有问题的商品。询问购买时间、问题详情，提供退换方案。',
'ShoppingCart', 4, 1),
('面试模拟', '参加英语面试，回答面试官的问题', '面试官', 3,
'Good morning. Thank you for coming in today. Could you start by introducing yourself?',
'你是面试官，用户是求职者。面试市场营销专员岗位。提问自我介绍、工作经验、优缺点、职业规划等问题。每次只问一个问题。',
'Briefcase', 5, 1),
('看病就医', '在医院看医生，描述症状并听取医嘱', '医生', 4,
'Hello, I am Doctor Smith. What seems to be the problem today?',
'你是医生，用户是患者。询问症状、持续时间、严重程度，给出诊断和建议。涉及感冒、头痛、胃痛等常见病症。',
'FirstAidKit', 6, 1);

-- 新增菜单：场景管理(管理员) + 场景练习(用户)
INSERT INTO sys_menu (parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible) VALUES
(1, '场景管理', 'scene', 'system/scene', 'system:scene:list', 'Film', 'C', 3, 1);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, (SELECT LAST_INSERT_ID()));

-- 会话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    scene_id    BIGINT COMMENT '关联场景ID(空则为自由对话)',
    title       VARCHAR(200) NOT NULL COMMENT '会话标题',
    is_starred  TINYINT DEFAULT 0 COMMENT '是否收藏 0否 1是',
    duration    INT DEFAULT 0 COMMENT '对话时长(秒)',
    round_count INT DEFAULT 0 COMMENT '对话轮数',
    error_count INT DEFAULT 0 COMMENT '总错误数',
    suggestion_count INT DEFAULT 0 COMMENT '总建议数',
    status      TINYINT DEFAULT 1 COMMENT '状态 1进行中 2已结束',
    summary     TEXT COMMENT 'AI生成的学习总结',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conv_user (user_id),
    INDEX idx_conv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 会话消息表
CREATE TABLE IF NOT EXISTS conversation_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role            VARCHAR(20) NOT NULL COMMENT 'USER或ASSISTANT',
    content         TEXT NOT NULL COMMENT '消息内容',
    grammar_json    TEXT COMMENT '完整的纠错JSON',
    has_error       TINYINT DEFAULT 0 COMMENT '是否有错误',
    has_suggestion  TINYINT DEFAULT 0 COMMENT '是否有建议',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话消息表';

-- 打卡记录表
CREATE TABLE IF NOT EXISTS user_checkin (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT NOT NULL COMMENT '用户ID',
    checkin_date     DATE NOT NULL COMMENT '打卡日期(YYYY-MM-DD)',
    checkin_type     TINYINT DEFAULT 1 COMMENT '打卡方式 1=自由对话 2=场景练习',
    total_seconds    INT DEFAULT 0 COMMENT '当日练习总时长(秒)',
    total_rounds     INT DEFAULT 0 COMMENT '当日对话总轮数',
    total_errors     INT DEFAULT 0 COMMENT '当日语法错误总数',
    total_suggestions INT DEFAULT 0 COMMENT '当日获得建议总数',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, checkin_date),
    INDEX idx_checkin_date (checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户打卡记录表';

-- 打卡统计表(冗余加速查询)
CREATE TABLE IF NOT EXISTS user_checkin_stats (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    streak_count     INT DEFAULT 0 COMMENT '当前连续打卡天数',
    max_streak       INT DEFAULT 0 COMMENT '历史最长连续打卡天数',
    total_days       INT DEFAULT 0 COMMENT '累计打卡天数',
    total_seconds    INT DEFAULT 0 COMMENT '累计练习时长(秒)',
    total_rounds     INT DEFAULT 0 COMMENT '累计对话轮数',
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户打卡统计表';

