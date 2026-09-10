# AI 英语口语对话系统项目文档

## 1. 项目概述

### 1.1 项目名称

AI 英语口语对话系统。

### 1.2 项目背景

传统英语口语练习通常依赖教师、同伴或固定录音材料，存在练习时间受限、反馈不及时和场景不够丰富等问题。本项目利用浏览器录音、实时语音识别、语音合成和大语言模型技术，构建一个面向个人用户的英语口语训练平台。

用户可以通过语音或文字与 AI 进行自由对话，也可以选择预设的生活和学习场景进行针对性练习。系统能够保存会话记录，并在场景练习结束后生成练习报告。同时，系统支持语法纠错、语音播放、学习打卡和管理员配置等功能。

### 1.3 项目目标

1. 实现浏览器端音频采集和语音转文字。
2. 实现用户与 AI 的流式对话体验。
3. 提供适合英语口语训练的场景化对话功能。
4. 对用户表达进行语法错误识别和改进建议反馈。
5. 保存用户会话、练习记录和学习打卡数据。
6. 通过 JWT 和服务端密钥管理保护用户和第三方服务配置。
7. 提供后台用户、角色和练习场景管理能力。

### 1.4 用户角色

系统主要包含两类用户：

- 普通用户：注册、登录、语音对话、场景练习、查看练习记录、每日打卡和维护个人资料。
- 管理员：拥有普通用户功能，并可以管理系统用户、角色和口语练习场景。

## 2. 系统需求分析

### 2.1 功能需求

#### 用户认证

- 用户注册
- 用户登录
- JWT 身份认证
- 获取当前用户信息
- 退出登录
- 管理员菜单和权限加载

#### AI 语音对话

- 选择 AI 发音人
- 调节语速
- 浏览器麦克风录音
- 录音音频转换为 16 kHz、16 bit、单声道 PCM
- 语音识别结果展示
- 文字输入对话
- AI 回复流式显示
- AI 回复通过讯飞 TTS 播放
- 清空当前对话

#### 场景练习

- 查看启用的口语练习场景
- 查看场景名称、难度、AI 角色和场景描述
- 根据场景提示词进行对话
- 使用预设开场白启动练习
- 统计对话轮数、练习时长和语法错误数量
- 结束练习并生成练习报告

#### 语法纠错

- 识别用户表达中的语法问题
- 展示错误内容和修改建议
- 展示替代表达和说明信息
- 对语法纠错结果进行语音播放
- 在场景练习中统计语法错误数量

#### 会话管理

- 创建会话
- 查询当前用户的会话列表
- 查看会话详情
- 查看会话消息
- 添加会话消息
- 结束会话
- 删除会话
- 收藏或取消收藏会话
- 获取当前进行中的会话
- 对长对话生成上下文摘要

#### 学习打卡

- 查看今日打卡状态
- 查看累计学习统计
- 查看月度打卡日历

#### 管理功能

- 用户管理
- 角色管理
- 练习场景管理
- 菜单权限管理
- DeepSeek 平台密钥配置
- AI 用量记录

### 2.2 非功能需求

#### 易用性

系统以任务为中心设计，用户登录后可以直接进入语音对话页面。录音、识别、AI 思考、AI 回复和错误状态均通过文字明确提示。

#### 安全性

第三方服务的 APISecret、APIKey 和平台级配置不暴露给浏览器。用户请求经过 JWT 认证过滤器，管理员接口需要管理员权限。平台 DeepSeek Key 以 AES-GCM 密文形式保存。

#### 性能

AI 对话使用 SSE 流式传输，避免等待完整回答后再一次性显示。语音识别支持实时 WebSocket 代理，减少浏览器与第三方服务的直接耦合。

#### 可维护性

后端按照 Controller、Service、Mapper、Entity 和 DTO 分层组织。第三方服务接入逻辑集中在讯飞和 DeepSeek 相关服务类中，便于替换服务供应商或扩展功能。

## 3. 系统总体设计

### 3.1 总体架构

系统采用前后端分离架构，整体分为表现层、业务层、数据访问层和第三方服务层。

```text
浏览器客户端
    |
    | HTTP REST / SSE / WebSocket
    v
Spring Boot 后端
    |
    +-- Controller 接口层
    +-- Security JWT 认证层
    +-- Service 业务层
    +-- Mapper 数据访问层
    +-- MySQL 数据库
    |
    +-- 讯飞语音识别服务
    +-- 讯飞语音合成服务
    +-- DeepSeek 大语言模型服务
```

### 3.2 前端架构

前端位于 `frontend` 目录，采用 Vue 3 Composition API 和 TypeScript 开发，使用 Vite 作为构建工具，Element Plus 作为基础组件库。

主要目录职责如下：

| 目录 | 主要职责 |
| --- | --- |
| `src/api` | 封装 REST API 请求 |
| `src/components` | 聊天、转写、语音合成等可复用组件 |
| `src/composables` | 语音转写等组合式业务逻辑 |
| `src/layouts` | 用户端和管理端页面布局 |
| `src/router` | 路由定义和登录权限守卫 |
| `src/store` | 用户 Token、用户信息和菜单状态 |
| `src/views` | 页面级视图 |
| `src/types` | TypeScript 类型定义 |
| `src/services` | 与语音处理相关的服务封装 |

### 3.3 后端架构

后端位于 `backend` 目录，基于 Spring Boot 3.5 和 Java 21 开发。

| 包 | 主要职责 |
| --- | --- |
| `config` | Spring Security、WebSocket、第三方服务和 Web 配置 |
| `controller` | 对外提供 REST、SSE 和文件接口 |
| `dto` | 接口请求和响应数据结构 |
| `entity` | 数据库实体对象 |
| `mapper` | MyBatis-Plus 数据访问接口 |
| `service` | 系统核心业务逻辑 |
| `security` | JWT 认证过滤器和当前用户工具 |
| `util` | JWT、签名、字符串和语法结果解析工具 |
| `exception` | 统一异常处理 |

## 4. 主要功能模块设计

### 4.1 用户认证模块

用户在前端填写用户名和密码后，前端向 `/api/auth/login` 发送登录请求。后端验证用户信息，验证通过后生成 JWT，并将用户编号、角色等信息返回给前端。前端将 Token 保存到浏览器本地存储，后续请求通过 `Authorization: Bearer Token` 传递。

后端的 `JwtAuthenticationFilter` 在请求进入业务接口前解析 Token，并将认证信息写入 Spring Security 上下文。控制器可以通过 `SecurityUtils` 获取当前登录用户编号。

登录流程如下：

```text
用户输入账号密码
    -> AuthController
    -> SysUserService 校验账号和密码
    -> JwtUtil 生成 Token
    -> 前端保存 Token 和用户信息
    -> 后续请求携带 Authorization 请求头
    -> JwtAuthenticationFilter 验证 Token
```

### 4.2 语音采集和识别模块

浏览器通过 `navigator.mediaDevices.getUserMedia` 获取麦克风权限，使用 Web Audio API 创建音频处理链。音频工作线程将录音数据转换为 PCM 数据，前端将数据以 16 kHz、16 bit、单声道格式提交后端。

系统支持两种语音识别方式：

1. 文件式转写：通过 `POST /api/transcriptions` 上传 PCM 或 MP3 文件，由 `TranscriptionService` 调用讯飞识别服务。
2. 实时转写：通过 WebSocket 连接 `/ws/transcriptions`，前端发送音频帧，后端由 `RealtimeTranscriptionHandler` 代理至讯飞实时识别服务，并向浏览器返回增量识别结果。

实时识别状态包括：

- `ready`：后端已准备好接收音频。
- `partial`：返回阶段性识别文本。
- `finishing`：正在结束识别。
- `final`：返回最终识别结果。
- `error`：识别过程发生错误。

### 4.3 AI 对话模块

前端通过 `POST /api/chat` 提交当前对话消息、发音人、场景提示词和会话编号。后端由 `ChatController` 完成以下工作：

1. 校验消息列表和用户身份。
2. 根据场景模式决定是否启用语法纠错。
3. 查询会话上下文摘要和用户偏好。
4. 通过 `ConversationContextBuilder` 组装系统提示词、上下文摘要和最近消息。
5. 调用 `DeepSeekClient` 发送流式请求。
6. 将 AI 输出通过 SSE 事件持续发送到前端。
7. 使用 `GrammarStreamParser` 解析语法纠错结构。
8. 记录大语言模型 Token 使用量。
9. 在上下文过长时异步生成新的上下文摘要。

SSE 事件主要包括：

| 事件 | 说明 |
| --- | --- |
| `meta` | 返回上下文是否截断、Token 估算和摘要状态 |
| `text` | AI 回复文本增量 |
| `grammar` | 语法纠错结构 |
| `done` | 当前回答完成 |
| `error` | 当前请求失败 |

### 4.4 语音合成模块

前端将待播放文本和发音人参数提交至 `POST /api/tts`。后端调用 `IflytekTtsClient` 请求讯飞 TTS 服务，并以音频响应返回浏览器。前端通过 `Blob` 创建音频对象并播放。

AI 回复会按照句号、问号、感叹号和换行符进行切分，并按顺序加入播放队列，从而实现边生成、边播放的交互效果。

### 4.5 场景练习模块

场景练习由预设的 `SpeakingScene` 数据驱动。每个场景包含场景名称、难度、AI 角色、描述、系统提示词和开场白等信息。

用户进入场景后，前端将场景提示词传递给聊天组件，后端据此构造场景化 system prompt。练习结束时，前端提交对话轮数、时长、语法错误数量和对话内容，后端通过 `PracticeRecordService` 保存记录并返回练习报告。

### 4.6 上下文管理模块

为避免长期对话超过模型上下文限制，系统采用“最近对话窗口加上下文摘要”的策略：

- 保留最近若干轮对话作为直接上下文。
- 将较早的对话压缩为 `context_summary`。
- 将用户偏好和长期学习记忆保存为 JSON。
- 在上下文过长时，通过异步任务调用模型生成新的摘要。
- 记录摘要操作产生的 Token 用量。

该机制可以在控制请求长度的同时保留用户对话的连续性。

### 4.7 学习打卡模块

打卡模块通过 `CheckinService` 提供今日状态、累计统计和月度日历。用户可以查看连续学习情况和历史打卡日期。相关数据通过用户编号进行隔离，保证不同用户只能读取自己的学习数据。

### 4.8 权限管理模块

系统采用角色和菜单结合的权限模型。登录成功后，管理员前端请求 `/api/auth/menus` 获取当前角色可访问的菜单。前端路由通过 `requiresAdmin` 元数据限制管理员页面访问，后端通过 Spring Security 和方法级权限机制进行服务端保护。

前端权限控制用于改善用户体验，后端权限校验用于保证实际安全性，二者不能互相替代。

## 5. 数据库设计

### 5.1 数据实体

根据当前后端实体和 Mapper 结构，系统主要包含以下数据对象：

| 实体 | 作用 |
| --- | --- |
| `SysUser` | 保存用户账号、密码、个人资料和用户偏好 |
| `SysRole` | 保存角色信息 |
| `SysMenu` | 保存系统菜单和权限信息 |
| `SysRoleMenu` | 建立角色和菜单之间的关联 |
| `SpeakingScene` | 保存口语练习场景 |
| `TranscriptionTask` | 保存语音转写任务及结果 |
| `Conversation` | 保存用户会话基本信息和上下文摘要 |
| `ConversationMessage` | 保存会话中的用户消息和 AI 消息 |
| `PracticeRecord` | 保存场景练习结果 |
| `UserCheckin` | 保存用户打卡记录 |
| `UserCheckinStats` | 保存或统计用户打卡数据 |
| `AppSetting` | 保存平台级配置密文 |
| `LlmUsageLog` | 保存大语言模型 Token 用量 |

### 5.2 重要字段设计

#### 用户偏好

`sys_user.preferences_json` 用于保存用户偏好和长期记忆，例如常见错误、薄弱场景和设置项。

#### 会话上下文摘要

`conversation.context_summary` 用于保存上下文管理摘要，与场景练习结束后生成的学习总结相区分。

#### LLM 用量日志

`llm_usage_log` 记录用户编号、会话编号、模型、输入 Token、输出 Token、总 Token、操作类型和创建时间，可以用于用量分析和成本统计。

#### 平台级配置

`app_setting.setting_value` 保存 AES-GCM 加密后的平台配置值，不保存明文 API Key。配置修改用户由 `update_by` 字段记录。

## 6. 接口设计

### 6.1 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |
| GET | `/api/auth/user-info` | 获取当前用户信息 |
| GET | `/api/auth/menus` | 获取当前用户菜单 |

### 6.2 语音接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/transcriptions` | 上传音频并转写 |
| GET | `/api/transcriptions` | 查询最近转写记录 |
| POST | `/api/tts` | 文字转语音 |
| WebSocket | `/ws/transcriptions` | 实时语音转写 |

### 6.3 AI 和会话接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat` | SSE 流式 AI 对话 |
| POST | `/api/conversations` | 创建会话 |
| GET | `/api/conversations` | 分页查询会话 |
| GET | `/api/conversations/{id}` | 查询会话详情 |
| GET | `/api/conversations/{id}/messages` | 查询会话消息 |
| POST | `/api/conversations/{id}/messages` | 添加会话消息 |
| PUT | `/api/conversations/{id}/end` | 结束会话 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| PUT | `/api/conversations/{id}/star` | 收藏或取消收藏 |
| GET | `/api/conversations/active` | 查询进行中的会话 |

### 6.4 场景和学习接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/scenes` | 查询启用场景 |
| GET | `/api/scenes/{id}` | 查询场景详情 |
| POST | `/api/scenes/{id}/finish` | 提交练习并生成报告 |
| GET | `/api/scenes/practice/records` | 查询当前用户练习记录 |
| GET | `/api/checkin/today` | 查询今日打卡状态 |
| GET | `/api/checkin/stats` | 查询累计统计 |
| GET | `/api/checkin/calendar` | 查询月度打卡日历 |

## 7. 安全设计

### 7.1 JWT 无状态认证

系统采用无状态会话，不在服务端保存传统 Session。登录成功后，后端签发 JWT。每次受保护请求都需要携带 Token，服务端验证签名、有效期和用户信息。

### 7.2 密码保护

用户密码不以明文保存，后端使用 `BCryptPasswordEncoder` 进行密码哈希和验证。

### 7.3 第三方密钥保护

浏览器只调用本系统后端，不直接调用讯飞和 DeepSeek 服务。讯飞密钥通过服务端环境变量加载，平台 DeepSeek Key 通过 AES-GCM 加密后保存到数据库。

部署时应设置固定的 `APP_SETTINGS_ENCRYPTION_KEY`。该密钥发生变化后，历史密文可能无法解密。

### 7.4 用户数据隔离

会话、消息、练习记录和打卡数据均与用户编号关联。后端在查询会话详情、消息和练习数据时验证资源归属，避免用户访问其他用户的数据。

### 7.5 CORS 和跨域

后端提供 CORS 配置，允许前端开发服务器通过代理访问后端接口。生产环境不建议长期使用完全开放的来源配置，应根据实际域名收紧允许来源。

## 8. 异常处理和状态反馈

系统通过统一异常处理类处理后端异常，并向前端返回明确的错误码和提示信息。典型错误包括：

- 用户未登录或 Token 过期
- 无权访问目标资源
- 麦克风权限被拒绝
- 音频格式或采样率不符合要求
- 讯飞服务连接失败
- DeepSeek 请求失败
- 数据库连接失败
- SSE 或 WebSocket 连接中断

前端通过 `aria-live` 和错误提示区域反馈识别失败、AI 对话失败和语音播放失败等状态。

## 9. 部署和运行说明

### 9.1 环境要求

- JDK 21
- Maven 3.9 或更高版本
- Node.js 22 或更高版本
- pnpm 10.14.0
- MySQL 8

### 9.2 数据库准备

创建数据库：

```sql
CREATE DATABASE speech_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

项目中的数据库脚本包括：

- `backend/context_management_ddl.sql`：上下文摘要、用户偏好和 LLM 用量日志相关结构。
- `backend/app_setting_ddl.sql`：平台级加密配置表。

部署时还需要根据项目完整初始化脚本创建用户、角色、菜单、场景、会话和业务记录等基础表，并配置初始管理员数据。

### 9.3 后端启动

```bash
export DB_USERNAME=root
export DB_PASSWORD=数据库密码
export APP_SETTINGS_ENCRYPTION_KEY=固定的32字节密钥
export IFLYTEK_APP_ID=讯飞应用ID
export IFLYTEK_API_KEY=讯飞APIKey
export IFLYTEK_API_SECRET=讯飞APISecret
export DEEPSEEK_API_KEY=DeepSeek APIKey

cd backend
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8080`。

### 9.4 前端启动

```bash
cd frontend
pnpm install
pnpm run dev
```

前端默认地址为 `http://localhost:5173`。开发服务器将 `/api` 和 `/ws` 请求代理到后端服务。

### 9.5 生产构建

```bash
cd frontend
pnpm run lint
pnpm run build

cd ../backend
mvn test
mvn package
```

## 10. 测试设计

### 10.1 已有测试

后端目前包含讯飞相关底层逻辑测试：

- `AudioValidatorTest`：测试音频参数和音频格式校验。
- `AuthSignerTest`：测试讯飞认证签名逻辑。
- `IatAuthSignerTest`：测试实时语音识别鉴权签名。
- `IatResponseCollectorTest`：测试讯飞识别响应的收集和解析。

### 10.2 建议补充的测试

为了满足论文中的完整测试要求，建议继续增加以下测试：

1. 用户注册、登录和密码错误测试。
2. JWT 过期、无效 Token 和无权限访问测试。
3. 会话归属校验测试。
4. 场景练习报告生成测试。
5. 打卡重复提交和跨用户查询测试。
6. DeepSeek 流式响应和异常响应测试。
7. 语法纠错 JSON 格式异常测试。
8. 前端语音录音权限拒绝和空音频测试。
9. 前端 SSE 中断、重试和错误展示测试。
10. 移动端布局和键盘操作测试。

## 11. 典型业务流程

### 11.1 自由语音对话流程

```text
用户登录
    -> 进入语音对话页面
    -> 选择 AI 发音人和语速
    -> 授权麦克风
    -> 开始录音
    -> 浏览器生成 PCM 音频
    -> 上传后端进行语音识别
    -> 获得用户文本
    -> 发送到 DeepSeek
    -> SSE 返回 AI 回复
    -> 按句调用讯飞 TTS
    -> 浏览器播放 AI 语音
    -> 保存用户消息和 AI 消息
```

### 11.2 场景练习流程

```text
用户进入场景列表
    -> 选择练习场景
    -> 加载场景提示词和开场白
    -> AI 播放开场白
    -> 用户进行多轮语音或文字对话
    -> 后端进行 AI 回复和语法分析
    -> 前端累计轮数、时长和错误数
    -> 用户点击结束练习
    -> 后端保存 PracticeRecord
    -> 返回练习报告
```

### 11.3 长会话上下文流程

```text
前端提交完整会话消息
    -> 后端读取已有 context_summary
    -> ConversationContextBuilder 生成模型上下文
    -> 保留最近消息并截断较早消息
    -> DeepSeek 流式生成回复
    -> 达到摘要条件后异步生成新摘要
    -> 更新 conversation.context_summary
```

## 12. 论文撰写建议

本项目可以按照以下章节组织毕业论文：

1. 绪论：介绍英语口语练习的现实需求和智能语音技术的发展。
2. 相关技术：介绍 Vue 3、Spring Boot、MyBatis-Plus、WebSocket、SSE、JWT、讯飞 API 和 DeepSeek API。
3. 需求分析：介绍系统用户角色、功能需求和非功能需求。
4. 系统总体设计：介绍系统架构、功能模块划分和数据流向。
5. 数据库设计：介绍实体关系、主要数据表和关键字段。
6. 系统详细设计与实现：介绍认证、语音识别、AI 对话、场景练习、语法纠错和打卡模块。
7. 系统测试：介绍测试环境、功能测试、安全测试、异常测试和结果分析。
8. 总结与展望：总结系统成果，并讨论多语言扩展、学习画像、发音评估和移动端适配等后续方向。

## 13. 当前项目注意事项

1. 配置文件中的数据库密码、讯飞密钥和 DeepSeek Key 应通过环境变量注入，不能提交真实凭证。
2. 生产环境应收紧 CORS 允许来源，不能长期使用完全开放的来源模式。
3. 部署前应确认完整数据库初始化脚本，当前仓库中已看到的 SQL 文件主要是增量结构脚本。
4. 前端当前录音自动停止时间与 README 中的描述需要统一，论文中应以最终实际代码和测试结果为准。
5. 实时 WebSocket 识别和文件式转写属于两条不同链路，论文中应分别说明其通信方式和适用场景。
6. 当前后端已有讯飞底层测试，但 AI 对话、权限、会话归属和前端交互仍建议补充自动化测试。
