package com.example.speech.service.iflytek;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.speech.config.IflytekIatProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IatAuthSignerTest {

    @Test
    void createsAuthenticatedUrlUsingDocumentedSignatureFormat() {
        IflytekIatProperties properties = new IflytekIatProperties(
                "app", "keyxxxxxxxx8ee279348519exxxxxxxx", "secretxxxxxxxx2df7900c09xxxxxxxx",
                URI.create("wss://iat.cn-huabei-1.xf-yun.com/v1"), Duration.ofSeconds(10), Duration.ofSeconds(75));
        Clock clock = Clock.fixed(Instant.parse("2024-05-14T08:43:39Z"), ZoneOffset.UTC);

        URI uri = new IatAuthSigner(properties, clock).createAuthenticatedUri();
        Map<String, String> query = Arrays.stream(uri.getRawQuery().split("&"))
                .map(item -> item.split("=", 2))
                .collect(Collectors.toMap(item -> item[0], item -> decode(item[1])));
        String authorization = new String(Base64.getDecoder().decode(query.get("authorization")),
                StandardCharsets.UTF_8);

        assertThat(uri.getScheme()).isEqualTo("wss");
        assertThat(query.get("date")).isEqualTo("Tue, 14 May 2024 08:43:39 GMT");
        assertThat(query.get("host")).isEqualTo("iat.cn-huabei-1.xf-yun.com");
        assertThat(authorization).contains("api_key=\"keyxxxxxxxx8ee279348519exxxxxxxx\"")
                .contains("headers=\"host date request-line\"")
                .contains("signature=\"");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
