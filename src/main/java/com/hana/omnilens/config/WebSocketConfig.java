package com.hana.omnilens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PartnerWebSocketHandshakeInterceptor partnerWebSocketHandshakeInterceptor;
    private final PartnerTopicAuthorizationInterceptor partnerTopicAuthorizationInterceptor;

    public WebSocketConfig(
            PartnerWebSocketHandshakeInterceptor partnerWebSocketHandshakeInterceptor,
            PartnerTopicAuthorizationInterceptor partnerTopicAuthorizationInterceptor) {
        this.partnerWebSocketHandshakeInterceptor = partnerWebSocketHandshakeInterceptor;
        this.partnerTopicAuthorizationInterceptor = partnerTopicAuthorizationInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/alerts")
                .addInterceptors(partnerWebSocketHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(partnerTopicAuthorizationInterceptor);
    }
}
