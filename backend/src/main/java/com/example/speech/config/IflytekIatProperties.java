package com.example.speech.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iflytek.iat")
public record IflytekIatProperties(
        String appId,
        String apiKey,
        String apiSecret,
        URI endpoint,
        Duration connectTimeout,
        Duration resultTimeout
) {
    public void validateCredentials() {
        if (isBlank(appId) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("讯飞 IAT 凭证未配置");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
