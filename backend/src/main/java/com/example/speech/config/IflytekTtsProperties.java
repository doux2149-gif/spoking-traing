package com.example.speech.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iflytek.tts")
public record IflytekTtsProperties(
        String appId,
        String apiKey,
        String apiSecret,
        URI endpoint,
        Duration connectTimeout,
        Duration resultTimeout,
        String vcn,
        int speed,
        int volume,
        int pitch
) {
    public void validateCredentials() {
        if (isBlank(appId) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("讯飞 TTS 凭证未配置");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
