package com.hana.omnilens.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hana.omnilens.config.ApiSignatureProperties;

@Component
public class InMemoryApiSignatureNonceStore implements ApiSignatureNonceStore {

    private final Clock clock;
    private final ApiSignatureProperties properties;
    private final ConcurrentHashMap<String, Instant> nonces = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryApiSignatureNonceStore(ApiSignatureProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryApiSignatureNonceStore(ApiSignatureProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean remember(String apiKeyFingerprint, String nonce, Instant expiresAt) {
        purgeExpired();
        trimIfNeeded();
        return nonces.putIfAbsent(apiKeyFingerprint + ":" + nonce, expiresAt) == null;
    }

    private void purgeExpired() {
        Instant now = Instant.now(clock);
        nonces.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private void trimIfNeeded() {
        int overflow = nonces.size() - properties.maxNonces();
        if (overflow <= 0) {
            return;
        }
        nonces.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(nonces::remove);
    }
}
