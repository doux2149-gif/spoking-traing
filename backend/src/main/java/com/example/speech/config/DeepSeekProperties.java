package com.example.speech.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        String apiKey,
        URI baseUrl,
        String model,
        int maxTokens,
        double temperature
) {
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key 未配置");
        }
    }
}
