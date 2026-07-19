package com.hana.omniconnect.security;

import java.time.Instant;

public interface ApiSignatureNonceStore {

    boolean remember(String apiKeyFingerprint, String nonce, Instant expiresAt);
}
