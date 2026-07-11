package com.hana.omnilens.portal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;
import com.hana.omnilens.security.PartnerCredentialRotationResult;
import com.hana.omnilens.security.PartnerCredentialRotationService;

@Service
public class PortalApiKeyApplicationService {

    private final PortalApiKeyApplicationRepository applicationRepository;
    private final PartnerCredentialRotationService credentialRotationService;
    private final PortalSecretCipher secretCipher;

    public PortalApiKeyApplicationService(
            PortalApiKeyApplicationRepository applicationRepository,
            PartnerCredentialRotationService credentialRotationService,
            PortalSecretCipher secretCipher) {
        this.applicationRepository = applicationRepository;
        this.credentialRotationService = credentialRotationService;
        this.secretCipher = secretCipher;
    }

    @Transactional
    public PortalApiKeyApplication request(PortalUser user) {
        boolean hasPendingOrApproved = applicationRepository.findByUserId(user.userId()).stream()
                .anyMatch(application -> application.status() == ApiKeyApplicationStatus.PENDING
                        || application.status() == ApiKeyApplicationStatus.APPROVED);
        if (hasPendingOrApproved) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE,
                    "An active API key application already exists.");
        }
        Instant now = Instant.now();
        PortalApiKeyApplication application = new PortalApiKeyApplication(
                "PAPP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT),
                user.userId(),
                "portal-" + user.userId().toLowerCase(Locale.ROOT),
                ApiKeyApplicationStatus.PENDING,
                now,
                null,
                null,
                null,
                null,
                null,
                now);
        applicationRepository.save(application);
        return application;
    }

    @Transactional
    public PortalApiKeyApplication approve(String applicationId, PortalUser administrator) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        PartnerCredentialRotationResult issued = credentialRotationService.rotate(application.partnerId());
        Instant now = Instant.now();
        PortalApiKeyApplication approved = new PortalApiKeyApplication(
                application.applicationId(), application.userId(), application.partnerId(), ApiKeyApplicationStatus.APPROVED,
                application.requestedAt(), now, administrator.userId(), secretCipher.encrypt(issued.apiKey()),
                issued.apiKeySha256Prefix(), null, now);
        applicationRepository.update(approved);
        return approved;
    }

    @Transactional
    public PortalApiKeyApplication reject(String applicationId, PortalUser administrator, String reason) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        Instant now = Instant.now();
        PortalApiKeyApplication rejected = new PortalApiKeyApplication(
                application.applicationId(), application.userId(), application.partnerId(), ApiKeyApplicationStatus.REJECTED,
                application.requestedAt(), now, administrator.userId(), null, null, reason.trim(), now);
        applicationRepository.update(rejected);
        return rejected;
    }

    public List<PortalApiKeyApplication> listForUser(PortalUser user) {
        return applicationRepository.findByUserId(user.userId());
    }

    public List<PortalApiKeyApplication> listAll() {
        return applicationRepository.findAll();
    }

    public String revealKey(PortalApiKeyApplication application, PortalUser user) {
        if (!application.userId().equals(user.userId()) && user.role() != PortalRole.ADMIN) {
            throw new BusinessException(ErrorCode.PORTAL_ACCESS_DENIED);
        }
        if (application.status() != ApiKeyApplicationStatus.APPROVED || application.encryptedApiKey() == null) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        return secretCipher.decrypt(application.encryptedApiKey());
    }

    public PortalApiKeyApplication find(String applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_APPLICATION_NOT_FOUND));
    }
}
