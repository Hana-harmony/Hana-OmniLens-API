package com.hana.omniconnect.portal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;
import com.hana.omniconnect.observability.BusinessEventPublisher;
import com.hana.omniconnect.security.PartnerCredentialRotationResult;
import com.hana.omniconnect.security.PartnerCredentialRotationService;

@Service
public class PortalApiKeyApplicationService {

    private final PortalApiKeyApplicationRepository applicationRepository;
    private final PartnerCredentialRotationService credentialRotationService;
    private final PortalSecretCipher secretCipher;
    private final BusinessEventPublisher businessEventPublisher;

    public PortalApiKeyApplicationService(
            PortalApiKeyApplicationRepository applicationRepository,
            PartnerCredentialRotationService credentialRotationService,
            PortalSecretCipher secretCipher,
            BusinessEventPublisher businessEventPublisher) {
        this.applicationRepository = applicationRepository;
        this.credentialRotationService = credentialRotationService;
        this.secretCipher = secretCipher;
        this.businessEventPublisher = businessEventPublisher;
    }

    @Transactional
    public PortalApiKeyApplication request(PortalUser user) {
        List<PortalApiKeyApplication> applications = applicationRepository.findByUserId(user.userId());
        boolean hasPendingOrApproved = applications.stream()
                .anyMatch(application -> application.status() == ApiKeyApplicationStatus.PENDING
                        || application.status() == ApiKeyApplicationStatus.APPROVED
                        || application.status() == ApiKeyApplicationStatus.REISSUE_REQUESTED
                        || application.status() == ApiKeyApplicationStatus.REVOCATION_REQUESTED);
        if (hasPendingOrApproved) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE,
                    "An active API key application already exists.");
        }
        Instant now = Instant.now();
        PortalApiKeyApplication terminal = applications.stream().findFirst().orElse(null);
        if (terminal != null) {
            PortalApiKeyApplication resubmitted = new PortalApiKeyApplication(
                    terminal.applicationId(), terminal.userId(), terminal.partnerId(), ApiKeyApplicationStatus.PENDING,
                    now, null, null, null, null, null, now);
            applicationRepository.resubmit(resubmitted);
            publishApplicationEvent("portal.api_key.requested", "API 이용 재신청", resubmitted);
            return resubmitted;
        }
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
        publishApplicationEvent("portal.api_key.requested", "API 이용 신청", application);
        return application;
    }

    @Transactional
    public PortalApiKeyApplication approve(String applicationId, PortalUser administrator) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.PENDING
                && application.status() != ApiKeyApplicationStatus.REISSUE_REQUESTED
                && application.status() != ApiKeyApplicationStatus.REVOCATION_REQUESTED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        Instant now = Instant.now();
        if (application.status() == ApiKeyApplicationStatus.REVOCATION_REQUESTED) {
            credentialRotationService.deactivate(application.partnerId());
            PortalApiKeyApplication revoked = reviewed(application, ApiKeyApplicationStatus.REVOKED,
                    administrator.userId(), null, null, null, now);
            applicationRepository.update(revoked);
            publishApplicationEvent("portal.api_key.revoked", "API 키 폐기 승인", revoked);
            return revoked;
        }
        PartnerCredentialRotationResult issued = credentialRotationService.rotate(application.partnerId());
        PortalApiKeyApplication approved = new PortalApiKeyApplication(
                application.applicationId(), application.userId(), application.partnerId(), ApiKeyApplicationStatus.APPROVED,
                application.requestedAt(), now, administrator.userId(), secretCipher.encrypt(issued.apiKey()),
                issued.apiKeySha256Prefix(), null, now);
        applicationRepository.update(approved);
        publishApplicationEvent("portal.api_key.approved", "API 이용 신청 승인", approved);
        return approved;
    }

    @Transactional
    public PortalApiKeyApplication reject(String applicationId, PortalUser administrator, String reason) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.PENDING
                && application.status() != ApiKeyApplicationStatus.REISSUE_REQUESTED
                && application.status() != ApiKeyApplicationStatus.REVOCATION_REQUESTED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        Instant now = Instant.now();
        ApiKeyApplicationStatus rejectedStatus = application.status() == ApiKeyApplicationStatus.PENDING
                ? ApiKeyApplicationStatus.REJECTED
                : ApiKeyApplicationStatus.APPROVED;
        PortalApiKeyApplication rejected = reviewed(application, rejectedStatus, administrator.userId(),
                application.encryptedApiKey(), application.apiKeySha256Prefix(), reason.trim(), now);
        applicationRepository.update(rejected);
        publishApplicationEvent("portal.api_key.reviewed", "API 이용 신청 검토", rejected);
        return rejected;
    }

    @Transactional
    public PortalApiKeyApplication cancel(String applicationId, PortalUser user) {
        PortalApiKeyApplication application = owned(applicationId, user);
        ApiKeyApplicationStatus next = switch (application.status()) {
            case PENDING -> ApiKeyApplicationStatus.CANCELLED;
            case REISSUE_REQUESTED, REVOCATION_REQUESTED -> ApiKeyApplicationStatus.APPROVED;
            default -> throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        };
        PortalApiKeyApplication cancelled = reviewed(application, next, null,
                application.encryptedApiKey(), application.apiKeySha256Prefix(), null, Instant.now());
        applicationRepository.update(cancelled);
        return cancelled;
    }

    @Transactional
    public PortalApiKeyApplication requestReissue(String applicationId, PortalUser user) {
        return requestAction(applicationId, user, ApiKeyApplicationStatus.REISSUE_REQUESTED);
    }

    @Transactional
    public PortalApiKeyApplication requestRevocation(String applicationId, PortalUser user) {
        return requestAction(applicationId, user, ApiKeyApplicationStatus.REVOCATION_REQUESTED);
    }

    @Transactional
    public PortalApiKeyApplication reissueNow(String applicationId, PortalUser administrator) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        PartnerCredentialRotationResult issued = credentialRotationService.rotate(application.partnerId());
        PortalApiKeyApplication reissued = reviewed(application, ApiKeyApplicationStatus.APPROVED,
                administrator.userId(), secretCipher.encrypt(issued.apiKey()), issued.apiKeySha256Prefix(), null,
                Instant.now());
        applicationRepository.update(reissued);
        return reissued;
    }

    @Transactional
    public PortalApiKeyApplication revokeNow(String applicationId, PortalUser administrator) {
        PortalApiKeyApplication application = find(applicationId);
        if (application.status() != ApiKeyApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        credentialRotationService.deactivate(application.partnerId());
        PortalApiKeyApplication revoked = reviewed(application, ApiKeyApplicationStatus.REVOKED,
                administrator.userId(), null, null, null, Instant.now());
        applicationRepository.update(revoked);
        return revoked;
    }

    public List<PortalApiKeyApplication> listForUser(PortalUser user) {
        return applicationRepository.findByUserId(user.userId());
    }

    public List<PortalApiKeyApplication> listAll() {
        return applicationRepository.findAll();
    }

    @Transactional
    public String revealKeyOnce(String applicationId, PortalUser user) {
        PortalApiKeyApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_APPLICATION_NOT_FOUND));
        if (!application.userId().equals(user.userId()) && user.role() != PortalRole.ADMIN) {
            throw new BusinessException(ErrorCode.PORTAL_ACCESS_DENIED);
        }
        if (application.encryptedApiKey() == null
                || application.status() != ApiKeyApplicationStatus.APPROVED
                && application.status() != ApiKeyApplicationStatus.REISSUE_REQUESTED
                && application.status() != ApiKeyApplicationStatus.REVOCATION_REQUESTED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        String apiKey = secretCipher.decrypt(application.encryptedApiKey());
        applicationRepository.clearEncryptedApiKey(application.applicationId(), Instant.now());
        return apiKey;
    }

    public PortalApiKeyApplication find(String applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_APPLICATION_NOT_FOUND));
    }

    private PortalApiKeyApplication requestAction(
            String applicationId,
            PortalUser user,
            ApiKeyApplicationStatus requestedStatus) {
        PortalApiKeyApplication application = owned(applicationId, user);
        if (application.status() != ApiKeyApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.API_KEY_APPLICATION_INVALID_STATE);
        }
        PortalApiKeyApplication requested = reviewed(application, requestedStatus, null,
                application.encryptedApiKey(), application.apiKeySha256Prefix(), null, Instant.now());
        applicationRepository.update(requested);
        return requested;
    }

    private PortalApiKeyApplication owned(String applicationId, PortalUser user) {
        PortalApiKeyApplication application = find(applicationId);
        if (!application.userId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.PORTAL_ACCESS_DENIED);
        }
        return application;
    }

    private PortalApiKeyApplication reviewed(
            PortalApiKeyApplication application,
            ApiKeyApplicationStatus status,
            String reviewer,
            String encryptedApiKey,
            String prefix,
            String reason,
            Instant now) {
        return new PortalApiKeyApplication(application.applicationId(), application.userId(), application.partnerId(), status,
                application.requestedAt(), now, reviewer, encryptedApiKey, prefix, reason, now);
    }

    private void publishApplicationEvent(String type, String title, PortalApiKeyApplication application) {
        businessEventPublisher.publish(type, title, java.util.Map.of(
                "applicationId", application.applicationId(),
                "partnerId", application.partnerId(),
                "status", application.status().name()));
    }
}
