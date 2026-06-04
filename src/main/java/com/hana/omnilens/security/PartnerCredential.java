package com.hana.omnilens.security;

public record PartnerCredential(
        String partnerId,
        String apiKeySha256
) {
}
