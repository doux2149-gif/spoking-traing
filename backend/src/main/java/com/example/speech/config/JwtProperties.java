package com.example.speech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret = "speech-service-default-secret-key-for-jwt-signing-purposes-2024";
    private Long expiration = 86400000L;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
