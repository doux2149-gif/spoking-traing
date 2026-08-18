# 语音助手

基于 Spring Boot、Vue 3、MyBatis-Plus 和 MySQL 的实时语音转写应用。浏览器采集麦克风音频并转换为 16kHz、16bit、单声道 PCM，通过后端 WebSocket 安全代理到讯飞，浏览器不会接触 `APISecret`。

## 运行条件

- JDK 21
- Maven 3.9+
- Node.js 22+
- pnpm 10.14.0
- MySQL 8

## 数据库

创建数据库：

```sql
CREATE DATABASE speech_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

表结构位于 `backend/src/main/resources/schema.sql`。首次启动可设置 `SQL_INIT_MODE=always` 自动执行。

## 后端配置与启动

```bash
source backend/.env
export DB_USERNAME=root
export DB_PASSWORD=你的数据库密码
cd backend
mvn spring-boot:run
```

后端默认运行于 `http://localhost:8080`。生产环境应通过系统环境变量或密钥管理服务注入凭证，不要提交真实密钥。

## 前端启动

```bash
cd frontend
pnpm install
pnpm run dev
```

访问 `http://localhost:5173`。开发服务器会把 `/api` 和 `/ws` 代理到 Spring Boot。首次点击“开始录音”时，需要允许浏览器使用麦克风。

## 接口

- `WS /ws/transcriptions`：实时上传 PCM 音频帧并接收增量文字
- `GET /api/transcriptions`：读取最近 10 条记录
- `POST /api/transcriptions`：保留的文件转写接口

实时连接先发送 `{ "type": "start", "language": "" }`，之后发送二进制 PCM 帧；停止时发送 `{ "type": "stop" }`。服务端返回 `ready`、`partial`、`finishing`、`final` 或 `error` 事件。

## 实时音频约束

- 浏览器自动转换为 16kHz、16bit、单声道 PCM
- 每帧约 40ms，即 640 个采样点、1280 字节
- 单次录音最长 60 秒
- 音频流不落盘，最终文字保存到 MySQL

## 校验

```bash
cd backend && mvn test
cd frontend && pnpm run lint && pnpm run build
```
