package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekIatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class IatMessageFactory {
    private final IflytekIatProperties properties;
    private final ObjectMapper objectMapper;

    public IatMessageFactory(IflytekIatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createFrame(byte[] audio, int length, int sequence, int status,
                              String encoding, int sampleRate, String language) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("app_id", properties.appId());
        header.put("status", status);

        if (status == 0) {
            ObjectNode iat = root.putObject("parameter").putObject("iat");
            iat.put("domain", "slm");
            iat.put("language", "mul_cn");
            iat.put("accent", "mandarin");
            if (language != null && !language.isBlank()) {
                iat.put("ln", language);
            }
            iat.put("eos", 6000);
            ObjectNode result = iat.putObject("result");
            result.put("encoding", "utf8");
            result.put("compress", "raw");
            result.put("format", "json");
        }

        ObjectNode audioNode = root.putObject("payload").putObject("audio");
        audioNode.put("encoding", encoding);
        audioNode.put("sample_rate", sampleRate);
        audioNode.put("channels", 1);
        audioNode.put("bit_depth", 16);
        audioNode.put("seq", sequence);
        audioNode.put("status", status);
        audioNode.put("audio", Base64.getEncoder().encodeToString(copy(audio, length)));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法创建语音识别请求", exception);
        }
    }

    private static byte[] copy(byte[] source, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, 0, result, 0, length);
        return result;
    }
}
