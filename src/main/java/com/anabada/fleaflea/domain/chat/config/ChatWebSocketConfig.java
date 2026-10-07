package com.anabada.fleaflea.domain.chat.config;

import com.anabada.fleaflea.domain.chat.security.ChatWebSocketAuthInterceptor;
import com.anabada.fleaflea.domain.chat.security.ChatWebSocketSessionDecorator;
import com.anabada.fleaflea.global.config.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final ChatWebSocketAuthInterceptor chatWebSocketAuthInterceptor;
    private final ChatWebSocketSessionDecorator chatWebSocketSessionDecorator;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws/chat")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(chatWebSocketTaskScheduler());
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatWebSocketAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(256 * 1024)
                .setSendTimeLimit(10000)
                .setTimeToFirstMessage(10000);
        registration.addDecoratorFactory(chatWebSocketSessionDecorator);
    }

    @Bean
    public ThreadPoolTaskScheduler chatWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("chat-websocket-");
        return taskScheduler;
    }
}
