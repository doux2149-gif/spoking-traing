package com.example.speech.service.iflytek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

final class IatResponseCollector {
    private final ObjectMapper objectMapper;
    private final CompletableFuture<IatResult> completion = new CompletableFuture<>();
    private final Map<Integer, String> fragments = new TreeMap<>();
    private String sid;

    IatResponseCollector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    synchronized IatUpdate accept(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode header = root.path("header");
            int code = header.path("code").asInt(-1);
            sid = header.path("sid").asText(sid);
            if (code != 0) {
                throw new IatServiceException(code, header.path("message").asText("识别服务调用失败"));
            }
            JsonNode result = root.path("payload").path("result");
            boolean hasText = result.hasNonNull("text");
            if (hasText) {
                collectDecodedResult(result.path("text").asText());
            }
            boolean finished = header.path("status").asInt() == 2;
            String text = String.join("", fragments.values());
            if (finished && !completion.isDone()) {
                completion.complete(new IatResult(text, sid));
            }
            return hasText || finished ? new IatUpdate(text, sid, finished) : null;
        } catch (IOException exception) {
            RuntimeException failure = new IllegalStateException("无法解析讯飞识别结果", exception);
            fail(failure);
            throw failure;
        } catch (RuntimeException exception) {
            fail(exception);
            throw exception;
        }
    }

    synchronized void fail(Throwable throwable) {
        completion.completeExceptionally(throwable);
    }

    CompletableFuture<IatResult> completion() {
        return completion;
    }

    private void collectDecodedResult(String encodedText) throws IOException {
        byte[] decoded = Base64.getDecoder().decode(encodedText);
        JsonNode result = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        if (result.has("ret") && result.path("ret").asInt() != 0) {
            throw new IatServiceException(result.path("ret").asInt(), "语音识别业务处理失败");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode wordSegment : result.path("ws")) {
            JsonNode candidates = wordSegment.path("cw");
            if (!candidates.isEmpty()) {
                text.append(candidates.get(0).path("w").asText());
            }
        }
        fragments.put(result.path("sn").asInt(fragments.size()), text.toString());
    }
}
