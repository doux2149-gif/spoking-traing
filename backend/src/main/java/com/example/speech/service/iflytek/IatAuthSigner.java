package com.example.speech.service.iflytek;

import com.example.speech.config.IflytekIatProperties;
import com.example.speech.util.IflytekAuthSigner;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 讯飞 IAT 鉴权 Spring 组件。
 * 实际签名逻辑委托给 {@link IflytekAuthSigner}，
 * 本类仅负责读取 IAT 配置并注入可测试的 Clock。
 */
@Component
public class IatAuthSigner {
    private final IflytekIatProperties properties;
    private final Clock clock;

    @Autowired
    public IatAuthSigner(IflytekIatProperties properties) {
        this(properties, Clock.systemUTC());
    }

    IatAuthSigner(IflytekIatProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public URI createAuthenticatedUri() {
        properties.validateCredentials();
        return IflytekAuthSigner.sign(
                properties.endpoint(), properties.apiKey(), properties.apiSecret(), clock);
    }
}
