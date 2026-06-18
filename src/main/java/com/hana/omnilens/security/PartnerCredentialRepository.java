package com.hana.omnilens.security;

import java.util.Optional;

public interface PartnerCredentialRepository {

    Optional<PartnerCredential> findActiveByApiKeySha256(String apiKeySha256);

    boolean existsAnyActive();
}
