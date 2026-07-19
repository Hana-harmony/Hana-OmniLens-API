package com.hana.omnilens.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omnilens.security.PartnerAuthentication;

@Component
public class PartnerTopicAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern PARTNER_TOPIC_PATTERN =
            Pattern.compile("^/topic/partners/([A-Za-z0-9._:-]+)/alerts$");
    private static final Pattern PARTNER_STOCK_TOPIC_PATTERN =
            Pattern.compile("^/topic/partners/([A-Za-z0-9._:-]+)/stocks/\\d{6}/alerts$");
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

		java.util.List<String> authenticatedPartnerIds = authenticatedPartnerIds(accessor);
        String destination = accessor.getDestination();
		if (authenticatedPartnerIds.isEmpty() || !StringUtils.hasText(destination)) {
            return message;
        }

		assertPartnerTopicAccess(authenticatedPartnerIds, destination);
        return message;
    }

	private void assertPartnerTopicAccess(java.util.List<String> authenticatedPartnerIds, String destination) {
        Matcher partnerTopic = PARTNER_TOPIC_PATTERN.matcher(destination);
        if (partnerTopic.matches()) {
			assertPartnerId(authenticatedPartnerIds, partnerTopic.group(1));
            return;
        }

        Matcher partnerStockTopic = PARTNER_STOCK_TOPIC_PATTERN.matcher(destination);
        if (partnerStockTopic.matches()) {
			assertPartnerId(authenticatedPartnerIds, partnerStockTopic.group(1));
            return;
        }

        throw new AccessDeniedException("Partner credential can subscribe only to its own partner topics");
    }

	private void assertPartnerId(java.util.List<String> authenticatedPartnerIds, String requestedPartnerId) {
		if (!authenticatedPartnerIds.contains(requestedPartnerId)) {
            throw new AccessDeniedException("Partner credential cannot subscribe to another partner topic");
        }
    }

	private java.util.List<String> authenticatedPartnerIds(StompHeaderAccessor accessor) {
		Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
		if (sessionAttributes == null) {
			return java.util.List.of();
		}
		Object partnerIds = sessionAttributes.get(PartnerAuthentication.PARTNER_IDS_ATTRIBUTE);
		if (partnerIds instanceof java.util.List<?> values) {
			return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
		}
		String partnerId = authenticatedPartnerId(accessor);
		return StringUtils.hasText(partnerId) ? java.util.List.of(partnerId) : java.util.List.of();
	}

    private String authenticatedPartnerId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return "";
        }
        Object partnerId = sessionAttributes.get(PartnerAuthentication.PARTNER_ID_ATTRIBUTE);
        return partnerId instanceof String value ? value : "";
    }
}
