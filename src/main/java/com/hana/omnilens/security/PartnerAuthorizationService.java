package com.hana.omnilens.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class PartnerAuthorizationService {

    public void assertPartnerAccess(String requestedPartnerId) {
		List<String> authenticatedPartnerIds = authenticatedPartnerIds();
		if (!authenticatedPartnerIds.contains(requestedPartnerId)) {
			throw new PartnerAccessDeniedException(String.join(",", authenticatedPartnerIds), requestedPartnerId);
        }
    }

    private String authenticatedPartnerId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object partnerId = request.getAttribute(PartnerAuthentication.PARTNER_ID_ATTRIBUTE);
            return partnerId instanceof String value ? value : "";
        }
        return "";
    }

	private List<String> authenticatedPartnerIds() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			Object partnerIds = attributes.getRequest().getAttribute(PartnerAuthentication.PARTNER_IDS_ATTRIBUTE);
			if (partnerIds instanceof List<?> values) {
				return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
			}
			String partnerId = authenticatedPartnerId();
			return StringUtils.hasText(partnerId) ? List.of(partnerId) : List.of();
		}
		return List.of();
	}
}
