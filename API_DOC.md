# 语音练习系统接口文档

> 本文档适用于网站前端、uni-app 和微信小程序。后端基于 Spring Boot，默认端口为 `8080`，接口统一使用 `/api` 前缀。

## 目录

- [通用约定](#一通用约定)
- [认证模块](#二认证模块-apiauth)
- [AI 对话](#三ai-对话-apichat)
- [会话管理](#四会话管理-apiconversations)
- [场景练习](#五场景练习-apiscenes)
- [打卡](#六打卡-apicheckin)
- [语音转写](#七语音转写-stt-apitranscriptions)
- [语音合成](#八语音合成-tts-apitts)
- [文件上传](#九文件上传-apiupload)
- [用户资料](#十用户资料-apisystemusers)
- [前端调用说明](#十一前端调用说明)
- [小程序对接要点](#十二小程序对接要点)

---

## 一、通用约定

### 1. Base URL

本地后端地址：

```text
http://localhost:8080
```

完整接口地址示例：

```text
http://localhost:8080/api/auth/login
```

线上环境需要将后端地址替换为实际域名。微信小程序必须在平台后台配置合法的 HTTPS 域名。

### 2. 鉴权方式

除登录和注册接口外，其余接口默认需要携带 JWT：

```http
Authorization: Bearer <token>
```

登录成功后，服务端在响应 `data.token` 中返回 token。token 失效或缺失时，服务端返回 `401`。

### 3. 通用响应格式

普通业务接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

状态码说明：

| code | 说明 |
|------|------|
| `200` | 请求成功 |
| `400` | 请求参数或业务校验失败 |
| `401` | 未登录或 token 已过期 |
| `403` | 无权访问该资源 |
| `404` | 资源不存在 |
| `500` | 服务端错误 |

### 4. 时间格式

时间字段使用 `LocalDateTime` 序列化格式：

```text
2026-08-14T10:23:45
```

---

## 二、认证模块 `/api/auth`

### 1. 登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "student01",
  "password": "123456"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOi...",
    "userId": 10,
    "username": "student01",
    "nickname": "小明",
    "avatar": "/uploads/avatars/avatar_10_xxx.png",
    "roleId": 2,
    "roleKey": "ROLE_USER"
  }
}
```

### 2. 注册

```http
POST /api/auth/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "newuser",
  "password": "123456",
  "email": "a@b.com",
  "nickname": "新用户"
}
```

校验规则：用户名长度为 3-50 位，密码长度为 6-100 位，邮箱必须符合邮箱格式。

### 3. 获取当前用户信息

```http
GET /api/auth/user-info
Authorization: Bearer <token>
```

成功响应中的 `data`：

```json
{
  "userId": 10,
  "username": "student01",
  "nickname": "小明",
  "avatar": "/uploads/avatars/avatar_10_xxx.png",
  "email": "a@b.com",
  "phone": "13800000000",
  "status": 1,
  "roleId": 2
}
```

---

## 三、AI 对话 `/api/chat`

这是 SSE 流式接口，响应类型为 `text/event-stream`。

```http
POST /api/chat
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream
```

请求体：

```json
{
  "messages": [
    { "role": "system", "content": "你是雅思口语考官" },
    { "role": "user", "content": "I want to practice speaking." },
    { "role": "assistant", "content": "Sure, let's begin." }
  ],
  "vcn": "catherine",
  "scenePrompt": "你是雅思口语考官，请逐步提问",
  "conversationId": 123
}
```

参数说明：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messages` | Array | 是 | 按时间升序排列的对话历史 |
| `vcn` | String | 否 | 发音人。`catherine` 或 `henry` 会触发英文语法纠错 |
| `scenePrompt` | String | 否 | 场景提示词，有值时进入场景练习模式 |
| `conversationId` | Long | 否 | 会话 ID，用于上下文摘要和用量记录 |

### SSE 事件

| 事件名 | data 格式 | 说明 |
|--------|-----------|------|
| `meta` | JSON 字符串 | 上下文信息 |
| `text` | 文本片段 | AI 回复内容，需要按顺序累加 |
| `grammar` | JSON 字符串 | 语法纠错结果 |
| `done` | 空字符串 | 本轮回复结束 |
| `error` | 文本 | 处理失败原因 |

`grammar` 事件示例：

```json
{
  "noError": false,
  "original": "I goes to school.",
  "correctText": "I go to school.",
  "errors": [
    {
      "wrongText": "goes",
      "correctText": "go",
      "reason": "主语 I 后动词用原形"
    }
  ],
  "speechText": "I go to school. 我去上学。",
  "suggestion": {
    "level": "better",
    "original": "I go to school.",
    "alternatives": ["I head to school.", "I make my way to school."],
    "tip": "head to 更地道，对应雅思词汇多样性",
    "speechText": "I head to school."
  }
}
```

---

## 四、会话管理 `/api/conversations`

以下接口均需要登录，并且只能操作当前用户自己的会话。

| 操作 | 方法 | 地址 |
|------|------|------|
| 创建会话 | `POST` | `/api/conversations` |
| 查询会话列表 | `GET` | `/api/conversations?page=1&size=10` |
| 获取会话详情 | `GET` | `/api/conversations/{id}` |
| 获取会话消息 | `GET` | `/api/conversations/{id}/messages` |
| 添加消息 | `POST` | `/api/conversations/{id}/messages` |
| 结束会话 | `PUT` | `/api/conversations/{id}/end` |
| 收藏或取消收藏 | `PUT` | `/api/conversations/{id}/star` |
| 删除会话 | `DELETE` | `/api/conversations/{id}` |
| 获取进行中会话 | `GET` | `/api/conversations/active` |

### 创建会话请求

```json
{
  "sceneId": 1,
  "title": "雅思 Part1 自我介绍"
}
```

### 添加消息请求

```json
{
  "role": "user",
  "content": "I goes to school.",
  "grammarJson": "{...语法纠错JSON...}",
  "hasError": true,
  "hasSuggestion": false
}
```

### 结束会话请求

```json
{
  "duration": 600,
  "summary": "本次练习重点练习了 Part1"
}
```

### 会话对象示例

```json
{
  "id": 123,
  "userId": 10,
  "sceneId": 1,
  "title": "雅思 Part1",
  "isStarred": 0,
  "duration": 600,
  "roundCount": 8,
  "errorCount": 3,
  "suggestionCount": 2,
  "status": 1,
  "summary": "...",
  "contextSummary": "...",
  "createTime": "2026-08-14T10:00:00",
  "updateTime": "2026-08-14T10:10:00"
}
```

---

## 五、场景练习 `/api/scenes`

| 操作 | 方法 | 地址 |
|------|------|------|
| 获取启用场景 | `GET` | `/api/scenes` |
| 获取场景详情 | `GET` | `/api/scenes/{id}` |
| 结束练习并生成报告 | `POST` | `/api/scenes/{id}/finish` |
| 获取我的练习记录 | `GET` | `/api/scenes/practice/records` |

### 结束练习请求

```json
{
  "rounds": 8,
  "duration": 600,
  "errorCount": 3,
  "conversation": "完整对话文本"
}
```

### 场景对象示例

```json
{
  "id": 1,
  "name": "雅思 Part1 自我介绍",
  "description": "...",
  "aiRole": "雅思口语考官",
  "difficulty": 2,
  "openingLine": "Good morning. Can you tell me your full name, please?",
  "systemPrompt": "你是雅思口语考试考官...",
  "icon": "/uploads/scenes/ielts.png",
  "sort": 1,
  "status": 1,
  "createTime": "2026-08-14T10:00:00",
  "updateTime": "2026-08-14T10:00:00"
}
```

---

## 六、打卡 `/api/checkin`

| 操作 | 方法 | 地址 |
|------|------|------|
| 今日打卡状态 | `GET` | `/api/checkin/today` |
| 累计打卡统计 | `GET` | `/api/checkin/stats` |
| 月度打卡日历 | `GET` | `/api/checkin/calendar?year=2026&month=8` |

`year` 和 `month` 不传时，默认返回当前年月。

---

## 七、语音转写 STT `/api/transcriptions`

### 1. 上传音频并转写

```http
POST /api/transcriptions
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `file` | File | 是 | - | 音频文件 |
| `encoding` | String | 否 | `raw` | 音频编码格式 |
| `sampleRate` | Integer | 否 | `16000` | 采样率 |
| `language` | String | 否 | 空 | 语种 |

返回示例：

```json
{
  "id": 100,
  "filename": "audio.pcm",
  "status": "completed",
  "text": "识别出来的文本",
  "sid": "iat...",
  "createdAt": "2026-08-14T10:00:00"
}
```

### 2. 获取最近转写记录

```http
GET /api/transcriptions
Authorization: Bearer <token>
```

---

## 八、语音合成 TTS `/api/tts`

```http
POST /api/tts
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "text": "要合成的文本",
  "vcn": "catherine",
  "speed": 50
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | String | 是 | UTF-8 字节长度不超过 8000 |
| `vcn` | String | 否 | 发音人 |
| `speed` | Integer | 否 | 语速，范围 0-100，默认 50 |

响应为二进制 MP3 音频：

```http
Content-Type: audio/mpeg
```

该接口不返回统一的 `Result` JSON。

---

## 九、文件上传 `/api/upload`

### 头像上传

```http
POST /api/upload/avatar
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单参数：

| 参数 | 类型 | 必填 | 限制 |
|------|------|------|------|
| `file` | File | 是 | 必须是图片，大小不超过 2MB |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "url": "/uploads/avatars/avatar_10_xxxxxxxx.jpg"
  }
}
```

上传流程：

1. 调用头像上传接口。
2. 获取响应中的 `data.url`。
3. 调用用户资料更新接口，将 `avatar` 设置为该 URL。

默认保存目录：

```text
backend/uploads/avatars/
```

可以通过环境变量修改：

```bash
UPLOAD_DIR=/Users/your-name/speech-uploads
```

上传文件通过以下地址访问：

```text
http://localhost:8080/uploads/avatars/{filename}
```

---

## 十、用户资料 `/api/system/users`

普通用户只能操作自己的用户信息，服务端会校验数据归属。

### 1. 获取用户详情

```http
GET /api/system/users/{id}
Authorization: Bearer <token>
```

### 2. 更新用户资料

```http
PUT /api/system/users/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "nickname": "新昵称",
  "avatar": "/uploads/avatars/avatar_10_xxx.png",
  "email": "new@b.com",
  "phone": "13900000000"
}
```

可用字段：`username`、`nickname`、`avatar`、`email`、`phone`、`status`、`roleId`。

普通用户只传需要修改的字段即可。

### 3. 修改密码

```http
PUT /api/system/users/{id}/password
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

---

## 十一、前端调用说明

### 1. Axios 配置

网站前端 Axios 的 `baseURL` 为 `/api`，因此接口代码中不要重复写 `/api`：

```ts
request.get('/auth/user-info')
request.put(`/system/users/${userId}`, data)
request.put(`/system/users/${userId}/password`, data)
request.post('/upload/avatar', formData)
```

实际请求地址分别是：

```text
/api/auth/user-info
/api/system/users/{id}
/api/system/users/{id}/password
/api/upload/avatar
```

### 2. 网站开发代理

前端开发服务器 `5173` 会将以下路径转发到后端 `8080`：

| 前端路径 | 后端地址 |
|----------|----------|
| `/api/**` | `http://localhost:8080` |
| `/uploads/**` | `http://localhost:8080` |
| `/ws/**` | `ws://localhost:8080` |

生产环境需要使用 Nginx 或其他反向代理配置相同的转发规则。

### 3. 头像上传示例

```ts
const formData = new FormData()
formData.append('file', file)

const uploadResult = await request.post('/upload/avatar', formData)
const avatarUrl = uploadResult.data.url

await request.put(`/system/users/${userId}`, {
  avatar: avatarUrl
})
```

---

## 十二、小程序对接要点

| 场景 | 接口 | 注意事项 |
|------|------|----------|
| 登录 | `POST /api/auth/login` | 将 `data.token` 保存到 storage |
| 获取用户信息 | `GET /api/auth/user-info` | 页面显示前校验 token |
| 录音识别 | `POST /api/transcriptions` | 使用 `wx.uploadFile`，推荐 PCM、16kHz、单声道 |
| AI 对话 | `POST /api/chat` | 使用 `enableChunked` 手动解析 SSE |
| 朗读 AI 回复 | `POST /api/tts` | 使用 `arraybuffer` 接收 MP3 |
| 场景列表 | `GET /api/scenes` | 获取启用中的场景 |
| 场景练习 | 创建会话后调用 `/api/chat` | 将 `systemPrompt` 作为 `scenePrompt` |
| 打卡 | `GET /api/checkin/today` | 获取今日状态 |
| 头像 | 上传接口加资料更新接口 | 需要调用两个接口 |

### SSE 分块接收示例

```js
const task = uni.request({
  url: BASE_URL + '/api/chat',
  method: 'POST',
  header: {
    Authorization: 'Bearer ' + token,
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  },
  data: { messages, vcn, scenePrompt, conversationId },
  enableChunked: true,
  responseType: 'text',
  success: () => {
    // 服务端响应结束
  }
})

let buffer = ''
task.onChunkReceived(({ data }) => {
  const chunk = decodeArrayBuffer(data)
  buffer += chunk

  const parsed = parseSseChunks(buffer)
  buffer = parsed.remainder

  for (const event of parsed.list) {
    if (event.event === 'text') {
      aiText += event.data
    } else if (event.event === 'grammar') {
      grammarData = JSON.parse(event.data)
    } else if (event.event === 'done') {
      // 本轮对话结束
    } else if (event.event === 'error') {
      // 显示错误信息
    }
  }
})
```

> 微信小程序原生 `wx.request` 不直接支持标准 SSE，需要使用基础库的分块接收能力，或在 uni-app H5 环境使用 SSE 客户端库。


## 一、通用约定

### 1. Base URL

```
http://localhost:8080        # 本地开发
https://你的域名               # 线上（小程序需在管理后台配置合法域名，且必须 HTTPS）
```

### 2. 鉴权方式

除 `登录` / `注册` 外，所有接口都需要在请求头携带 JWT：

```
Authorization: Bearer <token>
```

- token 由 `/api/auth/login` 返回，建议存入 `uni.setStorageSync('token', xxx)`
- token 失效或缺失时，后端返回 `401`，此时应跳转登录页

### 3. 统一响应格式

所有业务接口（除 `/api/chat` SSE、`/api/tts` 音频、`/api/transcriptions` 转写）均返回：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

- `code === 200` 表示成功；`401` 未登录；`403` 权限不足；`400/404/500` 业务/系统错误
- 前端封装统一拦截器：code != 200 时抛出 message

### 4. 时间格式

所有时间字段为 `LocalDateTime` 序列化后的字符串，如 `"2026-08-14T10:23:45"`。

---

## 二、认证模块 `/api/auth`

### 1. 登录

`POST /api/auth/login`

**请求体：**
```json
{
  "username": "student01",
  "password": "123456"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOi...",
    "userId": 10,
    "username": "student01",
    "nickname": "小明",
    "avatar": "/uploads/avatars/xxx.png",
    "roleId": 2,
    "roleKey": "ROLE_USER"
  }
}
```

### 2. 注册

`POST /api/auth/register`

**请求体：**
```json
{
  "username": "newuser",
  "password": "123456",
  "email": "a@b.com",
  "nickname": "新用户"
}
```

- 用户名 3-50 位，密码 6-100 位，email 需符合格式

### 3. 获取当前用户信息

`GET /api/auth/user-info`

**响应 data：**
```json
{
  "userId": 10,
  "username": "student01",
  "nickname": "小明",
  "avatar": "/uploads/avatars/xxx.png",
  "email": "a@b.com",
  "phone": "13800000000",
  "status": 1,
  "roleId": 2
}
```

> 小程序进入首页 `onShow` 时调用，token 失效则跳转登录页。

---

## 三、AI 对话 `/api/chat`（⚠️ SSE 流式，小程序需适配）

### 接口说明

`POST /api/chat`，`Content-Type: application/json`，响应类型 `text/event-stream`（SSE）。

后端通过 SSE 逐段推送 AI 回复，事件类型如下：

| 事件名 | data 内容 | 说明 |
|--------|-----------|------|
| `meta` | JSON 字符串 | 上下文信息（truncated/estimatedTokens/needSummarize/conversationId） |
| `text` | 纯文本片段 | AI 回复内容（逐段累加） |
| `grammar` | JSON 字符串 | 语法纠错结果（仅在英文 vcn 或场景模式且有纠错时推送） |
| `done` | 空字符串 | 本轮回复结束 |
| `error` | 错误文本 | 出错 |

**请求体：**
```json
{
  "messages": [
    { "role": "system", "content": "场景 system prompt（可选，由场景接口提供）" },
    { "role": "user", "content": "用户说的话" },
    { "role": "assistant", "content": "上一轮 AI 回复" }
  ],
  "vcn": "catherine",
  "scenePrompt": "你是雅思口语考官...",
  "conversationId": 123
}
```

- `messages`：完整对话历史（时间升序），必填
- `vcn`：发音人，英文值 `catherine`/`henry` 触发语法纠错；中文对话传中文发音人则跳过纠错
- `scenePrompt`：场景设定，有值则进入场景模式（同时触发语法纠错）
- `conversationId`：会话 ID，用于加载/更新上下文摘要、记录 token 用量

### grammar 事件 data 结构

```json
{
  "noError": false,
  "original": "I goes to school.",
  "correctText": "I go to school.",
  "errors": [
    { "wrongText": "goes", "correctText": "go", "reason": "主语 I 后动词用原形" }
  ],
  "speechText": "I go to school. 我去上学。",
  "suggestion": {
    "level": "better",
    "original": "I go to school.",
    "alternatives": ["I head to school.", "I make my way to school."],
    "tip": "head to 更地道，对应雅思词汇多样性",
    "speechText": "I head to school."
  }
}
```

- `suggestion.level`：`none`（无建议）/ `better`（雅思7分水平）/ `advanced`（雅思8分水平）
- `speechText`：用于 TTS 朗读（纠错用固定中文发音人 xiaoyan）

### ⚠️ 小程序对接方案

微信小程序原生 `wx.request` **不支持** SSE。三种方案：

1. **推荐：使用 `wx.request` + `enableChunked: true`（基础库 2.20.2+）**
   - 开启分块接收，在 `onChunkReceived` 回调里手动解析 `event: xxx\ndata: xxx\n\n` 文本
   - 注意：返回的是 ArrayBuffer，需 `TextDecoder` 解码（小程序需自行引入 polyfill）

2. **uni-app H5 模式**：直接用 `fetchEventSource`（与网站端一致）

3. **改造后端新增同步接口**：让后端返回完整 JSON（非流式），失去打字机效果但实现简单

---

## 四、会话管理 `/api/conversations`

所有接口需登录，且只能操作自己的会话。

### 1. 创建会话

`POST /api/conversations`

```json
{ "sceneId": 1, "title": "雅思 Part1 自我介绍" }
```

**响应 data：** `Conversation` 对象（见下方结构）

### 2. 分页查询会话列表

`GET /api/conversations?page=1&size=10`

**响应 data：** MyBatis-Plus 分页对象
```json
{
  "records": [ Conversation, ... ],
  "total": 25,
  "size": 10,
  "current": 1,
  "pages": 3
}
```

### 3. 获取会话详情

`GET /api/conversations/{id}`

### 4. 获取会话消息列表

`GET /api/conversations/{id}/messages`

**响应 data：** `ConversationMessage[]`

### 5. 添加消息到会话

`POST /api/conversations/{id}/messages`

```json
{
  "role": "user",
  "content": "用户说的话",
  "grammarJson": "{...语法纠错JSON...}",
  "hasError": true,
  "hasSuggestion": false
}
```

> 小程序在 SSE 接收到 `grammar` 事件后，应把 grammar JSON 落库到该用户消息；assistant 消息也通过此接口保存。

### 6. 结束会话

`PUT /api/conversations/{id}/end`

```json
{ "duration": 600, "summary": "本次练习重点练习了 Part1" }
```

### 7. 删除会话

`DELETE /api/conversations/{id}`

### 8. 收藏/取消收藏

`PUT /api/conversations/{id}/star`

### 9. 获取进行中的会话

`GET /api/conversations/active`

### Conversation 结构

```json
{
  "id": 123,
  "userId": 10,
  "sceneId": 1,
  "title": "雅思 Part1",
  "isStarred": 0,
  "duration": 600,
  "roundCount": 8,
  "errorCount": 3,
  "suggestionCount": 2,
  "status": 1,
  "summary": "...",
  "contextSummary": "...",
  "createTime": "2026-08-14T10:00:00",
  "updateTime": "2026-08-14T10:10:00"
}
```

### ConversationMessage 结构

```json
{
  "id": 456,
  "conversationId": 123,
  "role": "user",
  "content": "I goes to school.",
  "grammarJson": "{...}",
  "hasError": 1,
  "hasSuggestion": 0,
  "createTime": "2026-08-14T10:01:00"
}
```

---

## 五、场景练习 `/api/scenes`

### 1. 获取启用的场景列表

`GET /api/scenes`

**响应 data：** `SpeakingScene[]`

### 2. 获取场景详情

`GET /api/scenes/{id}`

> 详情包含 `systemPrompt`（作为 `/api/chat` 的 `scenePrompt`）和 `openingLine`（开场白，作为 AI 第一条消息）。

### 3. 结束练习生成报告

`POST /api/scenes/{id}/finish`

```json
{
  "rounds": 8,
  "duration": 600,
  "errorCount": 3,
  "conversation": "完整对话文本"
}
```

**响应 data：** `PracticeRecord`

### 4. 获取我的练习记录

`GET /api/scenes/practice/records`

**响应 data：** `PracticeRecord[]`

### SpeakingScene 结构

```json
{
  "id": 1,
  "name": "雅思 Part1 自我介绍",
  "description": "...",
  "aiRole": "雅思口语考官",
  "difficulty": 2,
  "openingLine": "Good morning. Can you tell me your full name, please?",
  "systemPrompt": "你是雅思口语考试考官...",
  "icon": "/uploads/scenes/ielts.png",
  "sort": 1,
  "status": 1,
  "createTime": "...",
  "updateTime": "..."
}
```

### PracticeRecord 结构

```json
{
  "id": 789,
  "userId": 10,
  "sceneId": 1,
  "sceneName": "雅思 Part1",
  "rounds": 8,
  "duration": 600,
  "errorCount": 3,
  "score": 75,
  "summary": "...",
  "createTime": "..."
}
```

---

## 六、打卡 `/api/checkin`

### 1. 今日打卡状态 + 统计

`GET /api/checkin/today`

### 2. 累计统计

`GET /api/checkin/stats`

### 3. 月度打卡日历

`GET /api/checkin/calendar?year=2026&month=8`

- year/month 不传则默认当前年月
- 响应为 `Map<日期字符串, 状态对象>`，用于渲染日历打勾

---

## 七、语音转写（STT）`/api/transcriptions`

> 小程序录音后上传音频文件，返回识别文本。用于「录音 → 识别 → 发送给 AI」流程。

`POST /api/transcriptions`

**请求：** `multipart/form-data`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| file | File | 是 | - | 音频文件 |
| encoding | String | 否 | `raw` | 编码格式，PCM 传 `raw` |
| sampleRate | int | 否 | `16000` | 采样率 |
| language | String | 否 | null | 语种 |

> 小程序建议用 PCM 格式录音（避免 MP3 解析问题），采样率 16000，单声道。

**响应（注意：本接口直接返回对象，不包 Result）：**
```json
{
  "id": 100,
  "filename": "audio.pcm",
  "status": "completed",
  "text": "识别出来的文本",
  "sid": "iat...",
  "createdAt": "2026-08-14T10:00:00"
}
```

> 小程序取 `data.text` 即识别结果。

### 获取最近 10 条转写记录

`GET /api/transcriptions`

---

## 八、语音合成（TTS）`/api/tts`

> AI 回复文本 → 语音播放。

`POST /api/tts`

**请求体：**
```json
{
  "text": "要合成的文本",
  "vcn": "catherine",
  "speed": 50
}
```

- `text`：必填，UTF-8 字节 ≤ 8000（约 2000 汉字）
- `vcn`：发音人；AI 回复用对话发音人，语法纠错朗读用 `xiaoyan`
- `speed`：语速 0-100，默认 50

**响应：** 直接返回二进制音频 `audio/mpeg`（mp3），**不包 Result**。

### 小程序对接

```js
// 1. 请求 TTS 拿到 arraybuffer
const res = await new Promise((resolve, reject) => {
  uni.request({
    url: BASE_URL + '/api/tts',
    method: 'POST',
    header: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    data: { text, vcn, speed },
    responseType: 'arraybuffer',
    success: resolve,
    fail: reject
  })
})
// 2. 写入临时文件并播放
const fs = uni.getFileSystemManager()
const path = `${wx.env.USER_DATA_PATH}/tts_${Date.now()}.mp3`
fs.writeFile({
  filePath: path,
  data: res.data,
  encoding: 'binary',
  success: () => {
    const audio = uni.createInnerAudioContext()
    audio.src = path
    audio.play()
  }
})
```

---

## 九、文件上传 `/api/upload`

### 头像上传

`POST /api/upload/avatar`

**请求：** `multipart/form-data`，字段 `file`（图片，≤ 2MB）

**响应 data：**
```json
{ "url": "/uploads/avatars/avatar_10_xxx.png" }
```

> 上传后调用「更新用户信息」接口把 url 写回用户记录。

---

## 十、用户资料 `/api/system/users`

> 注意：路径在 `/api/system/users`，但用户端接口（getById/update/resetPassword）允许本人操作自己的数据（后端 `assertDataOwnership` 校验）。

### 1. 获取用户详情

`GET /api/system/users/{id}`

### 2. 更新用户信息

`PUT /api/system/users/{id}`

```json
{
  "nickname": "新昵称",
  "avatar": "/uploads/avatars/xxx.png",
  "email": "new@b.com",
  "phone": "13900000000"
}
```

### 3. 修改密码

`PUT /api/system/users/{id}/password`

```json
{ "oldPassword": "旧密码", "newPassword": "新密码" }
```

> `UserUpdateRequest` 完整字段：username / nickname / avatar / email / phone / status / roleId。普通用户改资料时只传自己能改的字段即可。

---

## 十一、小程序对接要点速查

| 场景 | 接口 | 小程序注意点 |
|------|------|-------------|
| 登录 | `POST /api/auth/login` | 存 token 到 storage |
| 进入首页 | `GET /api/auth/user-info` | onShow 校验 token |
| 录音识别 | `POST /api/transcriptions` | 用 `wx.uploadFile`，PCM 16k 单声道 |
| AI 对话 | `POST /api/chat` | SSE 需 `enableChunked` 或改同步接口 |
| 朗读 AI 回复 | `POST /api/tts` | `responseType: arraybuffer` → 写文件播放 |
| 场景列表 | `GET /api/scenes` | - |
| 开始场景 | 创建会话 + 取 scene.systemPrompt + 调 /api/chat | scenePrompt 作为场景 system |
| 打卡 | `GET /api/checkin/today` | - |
| 头像 | `POST /api/upload/avatar` + `PUT /api/system/users/{id}` | 两步：先传再更新 |

### SSE 适配示例（enableChunked）

```js
const task = uni.request({
  url: BASE_URL + '/api/chat',
  method: 'POST',
  header: {
    'Authorization': 'Bearer ' + token,
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream'
  },
  data: { messages, vcn, scenePrompt, conversationId },
  enableChunked: true,           // 关键：开启分块
  responseType: 'text',
  success: () => { /* 全部结束 */ }
})

let buffer = ''
task.onChunkReceived(({ data }) => {
  // data 是 ArrayBuffer，需解码
  const chunk = decodeArrayBuffer(data)   // 需自行实现 TextDecoder
  buffer += chunk
  // 按 "event: xxx\ndata: yyy\n\n" 切分解析
  const events = parseSseChunks(buffer)
  buffer = events.remainder
  for (const ev of events.list) {
    if (ev.event === 'text')      aiText += ev.data
    else if (ev.event === 'grammar') grammarData = JSON.parse(ev.data)
    else if (ev.event === 'done')  /* 结束 */
    else if (ev.event === 'error') /* 出错 */
  }
})
```
