package com.example.speech.service;

import com.example.speech.entity.TranscriptionTask;
import com.example.speech.mapper.TranscriptionTaskMapper;
import com.example.speech.service.iflytek.IatUpdate;
import com.example.speech.service.iflytek.IflytekRealtimeClient;
import com.example.speech.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RealtimeTranscriptionHandler extends Endpoint {
    private final IflytekRealtimeClient realtimeClient;
    private final TranscriptionTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public RealtimeTranscriptionHandler(IflytekRealtimeClient realtimeClient,
                                        TranscriptionTaskMapper taskMapper,
                                        ObjectMapper objectMapper) {
        this.realtimeClient = realtimeClient;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onOpen(Session browserSession, EndpointConfig config) {
        browserSession.addMessageHandler(String.class,
                (MessageHandler.Whole<String>) message -> handleTextMessage(browserSession, message));
        browserSession.addMessageHandler(ByteBuffer.class,
                (MessageHandler.Whole<ByteBuffer>) message -> handleBinaryMessage(browserSession, message));
    }

    @Override
    public void onClose(Session browserSession, CloseReason closeReason) {
        SessionState state = sessions.remove(browserSession.getId());
        if (state != null) {
            state.upstream().abort();
            if (!"SUCCESS".equals(state.task().getStatus())) {
                markFailed(state.task(), "浏览器连接已断开");
            }
        }
    }

    @Override
    public void onError(Session browserSession, Throwable error) {
        SessionState state = sessions.get(browserSession.getId());
        if (state != null) {
            fail(browserSession, state, error);
        }
    }

    private void handleTextMessage(Session browserSession, String message) {
        try {
            JsonNode command = objectMapper.readTree(message);
            String type = command.path("type").asText();
            if ("start".equals(type)) {
                start(browserSession, command.path("language").asText(""));
            } else if ("stop".equals(type)) {
                stop(browserSession);
            } else {
                sendError(browserSession, "不支持的实时识别指令");
            }
        } catch (IOException exception) {
            sendError(browserSession, "无法解析实时识别指令");
        }
    }

    private void handleBinaryMessage(Session browserSession, ByteBuffer message) {
        SessionState state = sessions.get(browserSession.getId());
        if (state == null) {
            sendError(browserSession, "请先开始录音");
            return;
        }
        byte[] audio = new byte[message.remaining()];
        message.get(audio);
        try {
            state.upstream().sendAudio(audio);
        } catch (RuntimeException exception) {
            fail(browserSession, state, exception);
        }
    }

    private void start(Session browserSession, String language) {
        if (sessions.containsKey(browserSession.getId())) {
            sendError(browserSession, "录音已经开始");
            return;
        }
        TranscriptionTask task;
        try {
            task = createTask(language);
        } catch (RuntimeException exception) {
            sendError(browserSession, "无法创建转写任务，请确认数据库服务可用");
            return;
        }
        try {
            IflytekRealtimeClient.RealtimeSession upstream = realtimeClient.open(
                    language, new BrowserResultListener(browserSession, task));
            sessions.put(browserSession.getId(), new SessionState(upstream, task));
            send(browserSession, event("ready").put("taskId", task.getId()));
        } catch (RuntimeException exception) {
            markFailed(task, exception.getMessage());
            sendError(browserSession, exception.getMessage());
        }
    }

    private void stop(Session browserSession) {
        SessionState state = sessions.get(browserSession.getId());
        if (state == null) {
            sendError(browserSession, "当前没有正在进行的录音");
            return;
        }
        try {
            state.upstream().finish();
            send(browserSession, event("finishing"));
        } catch (RuntimeException exception) {
            fail(browserSession, state, exception);
        }
    }

    private void handleUpdate(Session browserSession, TranscriptionTask task, IatUpdate update) {
        ObjectNode response = event(update.finished() ? "final" : "partial");
        response.put("text", update.text());
        response.put("sid", update.sid());
        response.put("taskId", task.getId());
        if (update.finished()) {
            task.setStatus("SUCCESS");
            task.setResultText(update.text());
            task.setSid(update.sid());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            SessionState state = sessions.remove(browserSession.getId());
            if (state != null) {
                state.upstream().abort();
            }
        }
        send(browserSession, response);
    }

    private void fail(Session browserSession, SessionState state, Throwable error) {
        sessions.remove(browserSession.getId());
        state.upstream().abort();
        markFailed(state.task(), error.getMessage());
        sendError(browserSession, error.getMessage());
    }

    private TranscriptionTask createTask(String language) {
        LocalDateTime now = LocalDateTime.now();
        TranscriptionTask task = new TranscriptionTask();
        task.setOriginalFilename("实时录音 " + now.toLocalTime().withNano(0));
        task.setLanguage(language);
        task.setAudioEncoding("raw");
        task.setSampleRate(16000);
        task.setStatus("PROCESSING");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    private void markFailed(TranscriptionTask task, String message) {
        task.setStatus("FAILED");
        task.setErrorMessage(Strings.truncate(message, 1000));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private ObjectNode event(String type) {
        return objectMapper.createObjectNode().put("type", type);
    }

    private void sendError(Session session, String message) {
        send(session, event("error").put("message", message == null ? "实时识别失败" : message));
    }

    private void send(Session session, JsonNode payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
        } catch (IOException ignored) {
            // Connection cleanup is handled by onClose.
        }
    }

    private record SessionState(IflytekRealtimeClient.RealtimeSession upstream, TranscriptionTask task) {
    }

    private final class BrowserResultListener implements IflytekRealtimeClient.ResultListener {
        private final Session browserSession;
        private final TranscriptionTask task;

        private BrowserResultListener(Session browserSession, TranscriptionTask task) {
            this.browserSession = browserSession;
            this.task = task;
        }

        @Override
        public void onResult(IatUpdate update) {
            handleUpdate(browserSession, task, update);
        }

        @Override
        public void onError(Throwable error) {
            SessionState state = sessions.get(browserSession.getId());
            if (state != null) {
                fail(browserSession, state, error);
            }
        }
    }
}
