package com.hana.omniconnect.security;

import java.util.Optional;

public interface PartnerCredentialRepository {

    Optional<PartnerCredential> findActiveByApiKeySha256(String apiKeySha256);

    boolean existsAnyActive();

    int rotate(String partnerId, String apiKeySha256);

    int deactivate(String partnerId);
}
