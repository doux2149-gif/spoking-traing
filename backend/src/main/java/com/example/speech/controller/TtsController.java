package com.example.speech.controller;

import com.example.speech.service.iflytek.IflytekTtsClient;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {
    private final IflytekTtsClient ttsClient;

    public TtsController(IflytekTtsClient ttsClient) {
        this.ttsClient = ttsClient;
    }

    @PostMapping
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("合成文本不能为空");
        }
        if (request.text().getBytes(StandardCharsets.UTF_8).length > 8000) {
            throw new IllegalArgumentException("文本长度不能超过 8000 字节（约 2000 汉字）");
        }
        byte[] audio = ttsClient.synthesize(request.text(), request.vcn(), request.speed());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"speech.mp3\"")
                .body(audio);
    }

    public record TtsRequest(String text, String vcn, Integer speed) {}
}
