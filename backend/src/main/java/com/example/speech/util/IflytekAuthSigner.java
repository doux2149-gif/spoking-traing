package com.example.speech.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 讯飞接口通用鉴权工具类。
 * 负责生成 HMAC-SHA256 签名并拼接鉴权 URL，
 * 适用于 IAT、TTS 等所有讯飞 WebSocket 服务。
 */
public final class IflytekAuthSigner {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final DateTimeFormatter RFC1123_GMT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

    private IflytekAuthSigner() {
        // 工具类禁止实例化
    }

    /**
     * 根据讯飞凭证生成已签名的 WebSocket URI。
     *
     * @param endpoint  讯飞服务端点（含 path，不含 query）
     * @param apiKey    讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @return 已附加 authorization、date、host 参数的 URI
     */
    public static URI sign(URI endpoint, String apiKey, String apiSecret) {
        return sign(endpoint, apiKey, apiSecret, Clock.systemUTC());
    }

    /**
     * 可注入 Clock 的重载，便于单元测试。
     */
    public static URI sign(URI endpoint, String apiKey, String apiSecret, Clock clock) {
        String date = RFC1123_GMT.format(ZonedDateTime.now(clock).withZoneSameInstant(GMT));
        String requestLine = "GET " + endpoint.getRawPath() + " HTTP/1.1";
        String signatureOrigin = "host: " + endpoint.getHost() + "\ndate: " + date + "\n" + requestLine;
        String signature = hmacSha256(signatureOrigin, apiSecret);
        String authorizationOrigin = "api_key=\"" + apiKey
                + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                + signature + "\"";
        String authorization = Base64.getEncoder()
                .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
        String query = "authorization=" + encode(authorization)
                + "&date=" + encode(date)
                + "&host=" + encode(endpoint.getHost());
        return URI.create(endpoint + "?" + query);
    }

    private static String hmacSha256(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("无法生成讯飞接口签名", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
