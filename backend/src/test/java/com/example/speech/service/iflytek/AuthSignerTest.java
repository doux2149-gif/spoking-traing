package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekIatProperties;
import java.net.URI;
import java.time.Duration;

public class AuthSignerTest {
    public static void main(String[] args) {
        IflytekIatProperties props = new IflytekIatProperties(
                System.getenv("IFLYTEK_APP_ID"),
                System.getenv("IFLYTEK_API_KEY"),
                System.getenv("IFLYTEK_API_SECRET"),
                URI.create("wss://iat.cn-huabei-1.xf-yun.com/v1"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));
        IatAuthSigner signer = new IatAuthSigner(props);
        URI uri = signer.createAuthenticatedUri();
        System.out.println("Generated URI:");
        System.out.println(uri);
        System.out.println();
        System.out.println("Scheme: " + uri.getScheme());
        System.out.println("Host: " + uri.getHost());
        System.out.println("Path: " + uri.getPath());
        System.out.println("Query length: " + (uri.getQuery() != null ? uri.getQuery().length() : 0));
    }
}
