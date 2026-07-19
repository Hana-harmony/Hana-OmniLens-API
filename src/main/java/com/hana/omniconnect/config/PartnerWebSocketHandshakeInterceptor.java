package com.hana.omniconnect.config;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.hana.omniconnect.security.PartnerAuthentication;

@Component
public class PartnerWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            copyAttribute(servletRequest, attributes, PartnerAuthentication.PARTNER_ID_ATTRIBUTE);
			copyPartnerIds(servletRequest, attributes);
            copyAttribute(servletRequest, attributes, PartnerAuthentication.API_KEY_FINGERPRINT_ATTRIBUTE);
        }
        return true;
    }

	private void copyPartnerIds(ServletServerHttpRequest request, Map<String, Object> attributes) {
		Object value = request.getServletRequest().getAttribute(PartnerAuthentication.PARTNER_IDS_ATTRIBUTE);
		if (value instanceof java.util.List<?> partnerIds) {
			attributes.put(PartnerAuthentication.PARTNER_IDS_ATTRIBUTE, java.util.List.copyOf(partnerIds));
		}
	}

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private void copyAttribute(
            ServletServerHttpRequest request,
            Map<String, Object> attributes,
            String attributeName) {
        Object attribute = request.getServletRequest().getAttribute(attributeName);
        if (attribute instanceof String value && !value.isBlank()) {
            attributes.put(attributeName, value);
        }
    }
}
