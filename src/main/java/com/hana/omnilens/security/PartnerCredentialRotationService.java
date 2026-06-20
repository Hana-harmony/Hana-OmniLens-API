package com.hana.omnilens.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PartnerCredentialRotationService {

    private static final int RAW_KEY_BYTES = 32;
    private static final int HASH_PREFIX_LENGTH = 12;

    private final PartnerCredentialRepository partnerCredentialRepository;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public PartnerCredentialRotationService(PartnerCredentialRepository partnerCredentialRepository) {
        this(partnerCredentialRepository, new SecureRandom(), Clock.systemUTC());
    }

    PartnerCredentialRotationService(
            PartnerCredentialRepository partnerCredentialRepository,
            SecureRandom secureRandom,
            Clock clock) {
        this.partnerCredentialRepository = partnerCredentialRepository;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public PartnerCredentialRotationResult rotate(String partnerId) {
        String rawApiKey = generateApiKey();
        String apiKeySha256 = sha256Hex(rawApiKey);
        int deactivatedCount = partnerCredentialRepository.rotate(partnerId, apiKeySha256);
        return new PartnerCredentialRotationResult(
                partnerId,
                rawApiKey,
                apiKeySha256.substring(0, HASH_PREFIX_LENGTH),
                Instant.now(clock),
                deactivatedCount);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
