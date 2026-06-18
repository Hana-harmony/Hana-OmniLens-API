package com.hana.omnilens.security;

import java.time.Instant;

public class UnavailableApiSignatureNonceStore implements ApiSignatureNonceStore {

    @Override
    public boolean remember(String apiKeyFingerprint, String nonce, Instant expiresAt) {
        throw new IllegalStateException("signature nonce store is unavailable");
    }
}
