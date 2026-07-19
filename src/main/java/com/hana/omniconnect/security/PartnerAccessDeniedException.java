package com.hana.omniconnect.security;

public class PartnerAccessDeniedException extends RuntimeException {

    private final String authenticatedPartnerId;
    private final String requestedPartnerId;

    public PartnerAccessDeniedException(String authenticatedPartnerId, String requestedPartnerId) {
        super("Authenticated partner cannot access requested partner resource");
        this.authenticatedPartnerId = authenticatedPartnerId;
        this.requestedPartnerId = requestedPartnerId;
    }

    public String authenticatedPartnerId() {
        return authenticatedPartnerId;
    }

    public String requestedPartnerId() {
        return requestedPartnerId;
    }
}
