package com.hana.omnilens.security;

import java.time.Instant;

public interface ApiSignatureNonceStore {

    boolean remember(String apiKeyFingerprint, String nonce, Instant expiresAt);
}
