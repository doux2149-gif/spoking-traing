package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekIatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class IflytekIatClient {
    private static final Duration FRAME_INTERVAL = Duration.ofMillis(40);

    private final IflytekIatProperties properties;
    private final IatAuthSigner authSigner;
    private final IatMessageFactory messageFactory;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IflytekIatClient(IflytekIatProperties properties, IatAuthSigner authSigner,
                            IatMessageFactory messageFactory, ObjectMapper objectMapper) {
        this.properties = properties;
        this.authSigner = authSigner;
        this.messageFactory = messageFactory;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public IatResult transcribe(byte[] audio, String encoding, int sampleRate,
                                int frameSize, String language) {
        properties.validateCredentials();
        IatResponseCollector collector = new IatResponseCollector(objectMapper);
        WebSocket socket = connect(collector);
        try {
            sendAudio(socket, audio, encoding, sampleRate, frameSize, language);
            return collector.completion().get(properties.resultTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("语音识别任务已中断", exception);
        } catch (Exception exception) {
            throw unwrap(exception);
        } finally {
            socket.abort();
        }
    }

    private WebSocket connect(IatResponseCollector collector) {
        try {
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(properties.connectTimeout())
                    .buildAsync(authSigner.createAuthenticatedUri(), new Listener(collector))
                    .join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("无法连接讯飞语音识别服务", exception.getCause());
        }
    }

    private void sendAudio(WebSocket socket, byte[] audio, String encoding,
                           int sampleRate, int frameSize, String language)
            throws IOException, InterruptedException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(audio)) {
            byte[] buffer = new byte[frameSize];
            int sequence = 1;
            int length;
            while ((length = input.read(buffer)) != -1) {
                int status = sequence == 1 ? 0 : 1;
                String frame = messageFactory.createFrame(
                        buffer, length, sequence++, status, encoding, sampleRate, language);
                socket.sendText(frame, true).join();
                Thread.sleep(FRAME_INTERVAL.toMillis());
            }
            String finalFrame = messageFactory.createFrame(
                    new byte[0], 0, sequence, 2, encoding, sampleRate, language);
            socket.sendText(finalFrame, true).join();
        }
    }

    private static RuntimeException unwrap(Exception exception) {
        Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("语音识别失败", cause);
    }

    private static final class Listener implements WebSocket.Listener {
        private final IatResponseCollector collector;
        private final StringBuilder message = new StringBuilder();

        private Listener(IatResponseCollector collector) {
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
                collector.accept(message.toString());
                message.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            collector.fail(error);
        }
    }
}
