package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekIatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

@Component
public class IflytekRealtimeClient {
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_AUDIO_BYTES = SAMPLE_RATE * 2 * 60;

    private final IflytekIatProperties properties;
    private final IatAuthSigner authSigner;
    private final IatMessageFactory messageFactory;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IflytekRealtimeClient(IflytekIatProperties properties, IatAuthSigner authSigner,
                                 IatMessageFactory messageFactory, ObjectMapper objectMapper) {
        this.properties = properties;
        this.authSigner = authSigner;
        this.messageFactory = messageFactory;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public RealtimeSession open(String language, ResultListener resultListener) {
        properties.validateCredentials();
        IatResponseCollector collector = new IatResponseCollector(objectMapper);
        try {
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(properties.connectTimeout())
                    .buildAsync(authSigner.createAuthenticatedUri(),
                            new UpstreamListener(collector, resultListener))
                    .join();
            return new RealtimeSession(socket, language);
        } catch (CompletionException exception) {
            throw new IllegalStateException("无法连接讯飞实时语音识别服务", exception.getCause());
        }
    }

    public interface ResultListener {
        void onResult(IatUpdate update);
        void onError(Throwable error);
    }

    public final class RealtimeSession {
        private final WebSocket socket;
        private final String language;
        private int sequence = 1;
        private int audioBytes;
        private boolean started;
        private boolean finished;

        private RealtimeSession(WebSocket socket, String language) {
            this.socket = socket;
            this.language = language;
        }

        public synchronized void sendAudio(byte[] audio) {
            if (finished) {
                return;
            }
            if (audio.length == 0) {
                return;
            }
            if (audioBytes + audio.length > MAX_AUDIO_BYTES) {
                throw new IllegalArgumentException("单次录音不能超过 60 秒");
            }
            int status = started ? 1 : 0;
            String frame = messageFactory.createFrame(
                    audio, audio.length, sequence++, status, "raw", SAMPLE_RATE, language);
            socket.sendText(frame, true).join();
            started = true;
            audioBytes += audio.length;
        }

        public synchronized void finish() {
            if (finished) {
                return;
            }
            if (!started) {
                throw new IllegalStateException("没有收到可识别的音频");
            }
            finished = true;
            String frame = messageFactory.createFrame(
                    new byte[0], 0, sequence, 2, "raw", SAMPLE_RATE, language);
            socket.sendText(frame, true).join();
        }

        public synchronized void abort() {
            finished = true;
            socket.abort();
        }
    }

    private static final class UpstreamListener implements WebSocket.Listener {
        private final IatResponseCollector collector;
        private final ResultListener resultListener;
        private final StringBuilder message = new StringBuilder();

        private UpstreamListener(IatResponseCollector collector, ResultListener resultListener) {
            this.collector = collector;
            this.resultListener = resultListener;
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
                try {
                    IatUpdate update = collector.accept(message.toString());
                    message.setLength(0);
                    if (update != null) {
                        resultListener.onResult(update);
                    }
                } catch (RuntimeException exception) {
                    resultListener.onError(exception);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return webSocket.sendPong(message);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            collector.fail(error);
            resultListener.onError(error);
        }
    }
}
