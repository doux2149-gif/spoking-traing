package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekTtsProperties;
import com.example.speech.util.IflytekAuthSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class IflytekTtsClient {
    private final IflytekTtsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IflytekTtsClient(IflytekTtsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public byte[] synthesize(String text, String vcn, Integer speed) {
        properties.validateCredentials();
        String actualVcn = (vcn == null || vcn.isBlank()) ? properties.vcn() : vcn;
        int actualSpeed = (speed != null && speed >= 0 && speed <= 100) ? speed : properties.speed();
        TtsCollector collector = new TtsCollector();
        WebSocket socket = connect(collector);
        try {
            String request = buildRequest(text, actualVcn, actualSpeed);
            socket.sendText(request, true).join();
            return collector.completion().get(properties.resultTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("语音合成任务已中断", exception);
        } catch (Exception exception) {
            throw unwrap(exception);
        } finally {
            socket.abort();
        }
    }

    private WebSocket connect(TtsCollector collector) {
        try {
            java.net.URI authUri = IflytekAuthSigner.sign(
                    properties.endpoint(), properties.apiKey(), properties.apiSecret());
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(properties.connectTimeout())
                    .buildAsync(authUri, new TtsListener(collector))
                    .join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("无法连接讯飞语音合成服务", exception.getCause());
        }
    }

    private String buildRequest(String text, String vcn, int speed) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("common").put("app_id", properties.appId());
        ObjectNode business = root.putObject("business");
        business.put("aue", "lame");
        business.put("sfl", 1);
        business.put("auf", "audio/L16;rate=16000");
        business.put("vcn", vcn);
        business.put("speed", speed);
        business.put("volume", properties.volume());
        business.put("pitch", properties.pitch());
        business.put("tte", "UTF8");
        ObjectNode data = root.putObject("data");
        data.put("status", 2);
        data.put("text", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建语音合成请求", exception);
        }
    }

    private static RuntimeException unwrap(Exception exception) {
        Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("语音合成失败", cause);
    }

    private static final class TtsCollector {
        private final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        synchronized void onAudio(byte[] audio) {
            buffer.write(audio, 0, audio.length);
        }

        synchronized void onComplete() {
            future.complete(buffer.toByteArray());
        }

        synchronized void onError(Throwable error) {
            future.completeExceptionally(error);
        }

        CompletableFuture<byte[]> completion() {
            return future;
        }
    }

    private static final class TtsListener implements WebSocket.Listener {
        private final TtsCollector collector;
        private final StringBuilder message = new StringBuilder();

        TtsListener(TtsCollector collector) {
            this.collector = collector;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                processMessage(message.toString());
                message.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            collector.onError(error);
        }

        private void processMessage(String json) {
            try {
                var mapper = new ObjectMapper();
                var root = mapper.readTree(json);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    collector.onError(new IllegalStateException(
                            "语音合成服务错误: " + code + " - " + root.path("message").asText()));
                    return;
                }
                var dataNode = root.path("data");
                if (dataNode.isMissingNode() || dataNode.isNull()) {
                    return;
                }
                String audio = dataNode.path("audio").asText("");
                if (!audio.isEmpty()) {
                    collector.onAudio(Base64.getDecoder().decode(audio));
                }
                int status = dataNode.path("status").asInt(0);
                if (status == 2) {
                    collector.onComplete();
                }
            } catch (Exception exception) {
                collector.onError(new IllegalStateException("无法解析语音合成结果", exception));
            }
        }
    }
}
