package com.example.speech.config;

import com.example.speech.service.RealtimeTranscriptionHandler;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import java.util.Set;
import org.apache.tomcat.websocket.server.WsSci;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final RealtimeTranscriptionHandler transcriptionHandler;

    public WebSocketConfig(RealtimeTranscriptionHandler transcriptionHandler) {
        this.transcriptionHandler = transcriptionHandler;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(context -> {
            context.addServletContainerInitializer(new WsSci(), Set.of());
            context.addServletContainerInitializer((classes, servletContext) -> registerEndpoint(servletContext),
                    Set.of());
        });
    }

    private void registerEndpoint(ServletContext servletContext) throws ServletException {
        Object attribute = servletContext.getAttribute(ServerContainer.class.getName());
        if (!(attribute instanceof ServerContainer container)) {
            throw new ServletException("WebSocket server container is unavailable");
        }
        ServerEndpointConfig endpoint = ServerEndpointConfig.Builder
                .create(RealtimeTranscriptionHandler.class, "/ws/transcriptions")
                .configurator(new SpringEndpointConfigurator(transcriptionHandler))
                .build();
        try {
            container.addEndpoint(endpoint);
        } catch (DeploymentException exception) {
            throw new ServletException("Unable to register transcription WebSocket", exception);
        }
    }

    private static final class SpringEndpointConfigurator extends ServerEndpointConfig.Configurator {
        private final RealtimeTranscriptionHandler handler;

        private SpringEndpointConfigurator(RealtimeTranscriptionHandler handler) {
            this.handler = handler;
        }

        @Override
        public <T> T getEndpointInstance(Class<T> endpointClass) {
            return endpointClass.cast(handler);
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            if (originHeaderValue == null) {
                return true;
            }
            return originHeaderValue.startsWith("http://localhost:")
                    || originHeaderValue.startsWith("http://127.0.0.1:")
                    || originHeaderValue.startsWith("https://servicewechat.com")
                    || originHeaderValue.startsWith("https://developers.weixin.qq.com");
        }
    }
}
