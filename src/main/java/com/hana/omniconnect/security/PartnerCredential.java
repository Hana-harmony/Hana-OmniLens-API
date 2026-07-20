package com.hana.omniconnect.security;

public record PartnerCredential(
        String partnerId,
        String apiKeySha256,
        boolean rateLimitExempt
) {
}
