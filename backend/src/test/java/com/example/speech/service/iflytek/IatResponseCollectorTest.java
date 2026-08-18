package com.example.speech.service.iflytek;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IatResponseCollectorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void decodesBase64ResultAndJoinsFragmentsBySequence() {
        IatResponseCollector collector = new IatResponseCollector(objectMapper);
        collector.accept(response(0, 2, false, "世界", 1));
        IatUpdate partial = collector.accept(response(0, 1, false, "你好，", 1));
        IatUpdate completed = collector.accept("{\"header\":{\"code\":0,\"message\":\"success\",\"sid\":\"session-1\",\"status\":2}}");

        IatResult result = collector.completion().join();

        assertThat(partial.text()).isEqualTo("你好，世界");
        assertThat(partial.finished()).isFalse();
        assertThat(completed.finished()).isTrue();

        assertThat(result.text()).isEqualTo("你好，世界");
        assertThat(result.sid()).isEqualTo("session-1");
    }

    private static String response(int code, int sequence, boolean last, String word, int status) {
        String text = "{\"sn\":" + sequence + ",\"ls\":" + last
                + ",\"ws\":[{\"cw\":[{\"w\":\"" + word + "\",\"sc\":0}]}]}";
        String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return "{\"header\":{\"code\":" + code + ",\"message\":\"success\",\"sid\":\"session-1\",\"status\":"
                + status + "},\"payload\":{\"result\":{\"text\":\"" + encoded + "\"}}}";
    }
}
