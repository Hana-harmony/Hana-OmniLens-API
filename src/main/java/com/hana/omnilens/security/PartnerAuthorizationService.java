package com.hana.omnilens.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class PartnerAuthorizationService {

    public void assertPartnerAccess(String requestedPartnerId) {
        String authenticatedPartnerId = authenticatedPartnerId();
        if (!StringUtils.hasText(authenticatedPartnerId)) {
            return;
        }
        if (!authenticatedPartnerId.equals(requestedPartnerId)) {
            throw new PartnerAccessDeniedException(authenticatedPartnerId, requestedPartnerId);
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
}
