package com.hana.omnilens.security;

public final class PartnerAuthentication {

    public static final String PARTNER_ID_ATTRIBUTE = "omnilens.authenticatedPartnerId";
	public static final String PARTNER_IDS_ATTRIBUTE = "omnilens.authenticatedPartnerIds";
    public static final String API_KEY_FINGERPRINT_ATTRIBUTE = "omnilens.apiKeyFingerprint";
    public static final String BOOTSTRAP_ATTRIBUTE = "omnilens.bootstrapCredential";

    private PartnerAuthentication() {
    }
}
