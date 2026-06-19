package com.hana.omnilens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.hana.omnilens.market.stream.MarketQuoteWebSocketHandler;

@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final PartnerWebSocketHandshakeInterceptor partnerWebSocketHandshakeInterceptor;
    private final PartnerTopicAuthorizationInterceptor partnerTopicAuthorizationInterceptor;
    private final MarketQuoteWebSocketHandler marketQuoteWebSocketHandler;

    public WebSocketConfig(
            PartnerWebSocketHandshakeInterceptor partnerWebSocketHandshakeInterceptor,
            PartnerTopicAuthorizationInterceptor partnerTopicAuthorizationInterceptor,
            MarketQuoteWebSocketHandler marketQuoteWebSocketHandler) {
        this.partnerWebSocketHandshakeInterceptor = partnerWebSocketHandshakeInterceptor;
        this.partnerTopicAuthorizationInterceptor = partnerTopicAuthorizationInterceptor;
        this.marketQuoteWebSocketHandler = marketQuoteWebSocketHandler;
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
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketQuoteWebSocketHandler, "/ws/market/quotes")
                .addInterceptors(partnerWebSocketHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(partnerTopicAuthorizationInterceptor);
    }
}
