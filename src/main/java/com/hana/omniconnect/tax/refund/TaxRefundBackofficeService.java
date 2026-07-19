package com.hana.omniconnect.tax.refund;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;
import com.hana.omniconnect.observability.BusinessEventPublisher;

@Service
public class TaxRefundBackofficeService {

    private final TaxRefundBackofficeRepository repository;
    private final BusinessEventPublisher businessEventPublisher;

    public TaxRefundBackofficeService(
            TaxRefundBackofficeRepository repository,
            BusinessEventPublisher businessEventPublisher) {
        this.repository = repository;
        this.businessEventPublisher = businessEventPublisher;
    }

    @Transactional
    public TaxRefundCaseSyncResponse sync(TaxRefundCaseSyncRequest request) {
        List<TaxRefundDocumentSnapshot> safeDocuments = validatedDocuments(request.verifiedDocuments());
        Instant now = Instant.now();
        TaxRefundBackofficeCase current = repository.findByCaseId(request.caseId()).orElse(null);
        String status = current == null ? "SYNCED_WITH_HANA" : current.status();
        String submissionStatus = current == null ? "NOT_SUBMITTED" : current.taxOfficeSubmissionStatus();
        Instant submittedAt = current == null ? null : current.taxOfficeSubmittedAt();
        repository.upsert(new TaxRefundBackofficeCase(request.caseId(), request.accountId(), request.userId(), request.taxYear(), request.treatyCountry(), "0.00", false, false, List.of(), safeDocuments, status, request.requestedAt(), now, submissionStatus, submittedAt,
                current == null ? "NOT_PREPARED" : current.correctionRequestStatus(),
                current == null ? null : current.correctionPdfSha256(),
                current == null ? null : current.correctionPreparedAt(),
                current == null ? null : current.approvedAt()));
        repository.replaceDocumentContents(request.caseId(), request.verifiedDocuments());
        if (current == null) {
            businessEventPublisher.publish("tax.refund_case.received", "세무 환급 신청 접수", Map.of(
                    "caseId", request.caseId(),
                    "taxYear", request.taxYear(),
                    "status", status));
        }
        return new TaxRefundCaseSyncResponse(request.caseId(), status, now, "HANA_OMNI_CONNECT_BACKOFFICE");
    }

    public List<TaxRefundBackofficeCase> list() { return repository.findAll(); }

    public TaxRefundBackofficeCase caseById(String caseId) {
        return repository.findByCaseId(caseId).orElseThrow(() -> new IllegalArgumentException("Tax refund case was not found."));
    }

    @Transactional
    public TaxRefundBackofficeCase approve(String caseId, String administratorId) {
        Instant now = Instant.now();
        TaxRefundBackofficeCase taxCase = caseById(caseId);
        if ("APPROVED".equals(taxCase.correctionRequestStatus())) {
            throw new BusinessException(ErrorCode.TAX_CORRECTION_ALREADY_APPROVED);
        }
        if (repository.findPreparedCorrectionPdf(caseId).isEmpty()) {
            throw new BusinessException(ErrorCode.TAX_CORRECTION_NOT_PREPARED);
        }
        repository.approve(caseId, administratorId, now);
        TaxRefundBackofficeCase approved = caseById(caseId);
        businessEventPublisher.publish("tax.correction.approved", "세무 경정청구 승인", Map.of(
                "caseId", approved.caseId(),
                "taxYear", approved.taxYear(),
                "status", approved.correctionRequestStatus()));
        return approved;
    }

    public void savePreparedCorrection(String caseId, Map<String, String> fields, byte[] pdf, String administratorId) {
        caseById(caseId);
        validateFields(fields);
        repository.savePreparedCorrection(caseId, fields, pdf, sha256(pdf), administratorId, Instant.now());
    }

    public byte[] preparedCorrectionPdf(String caseId) {
        caseById(caseId);
        return repository.findPreparedCorrectionPdf(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAX_CORRECTION_NOT_PREPARED));
    }

    public Map<String, String> initialCorrectionFields(String caseId) {
        TaxRefundBackofficeCase taxCase = caseById(caseId);
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("claimantName", firstField(taxCase, "taxpayer_name", "applicant_name", "name", "holder_name"));
        fields.put("birthDate", firstField(taxCase, "birth_date", "date_of_birth"));
        fields.put("claimantPhone", firstField(taxCase, "phone_number", "phone", "telephone"));
        fields.put("claimantResidence", firstFieldOrDefault(taxCase, taxCase.treatyCountry(), "residency_country", "residence_country", "country"));
        fields.put("residencyCountryCode", firstFieldOrDefault(taxCase, taxCase.treatyCountry(), "residency_country_code", "country_code"));
        fields.put("taxpayerIdentificationNumber", firstField(taxCase, "tin", "taxpayer_identification_number"));
        fields.put("claimantAddress", firstField(taxCase, "address", "residence_address"));
        fields.put("apostilleCertificateNumber", firstField(taxCase, "certificate_number"));
        fields.put("taxYear", String.valueOf(taxCase.taxYear()));
        fields.put("applicationDate", firstField(taxCase, "signature_date", "application_date"));
        fields.put("claimantSignatureName", firstField(taxCase, "applicant_name", "taxpayer_name"));
        fields.put("claimContent", "제한세율 적용을 위한 원천징수세액 경정을 청구합니다.");
        for (TaxRefundDocumentSnapshot document : taxCase.verifiedDocuments()) {
            if (document.extractedFields() == null) continue;
            document.extractedFields().forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) fields.putIfAbsent(key, value);
            });
        }
        return fields;
    }

    public Map<String, String> savedCorrectionFields(String caseId) {
        caseById(caseId);
        return repository.findCorrectionFields(caseId).orElseGet(() -> initialCorrectionFields(caseId));
    }

    public TaxRefundDocumentContent documentContent(String caseId, String documentId) {
        caseById(caseId);
        return repository.findDocumentContent(caseId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Tax document was not found."));
    }

    private String firstField(TaxRefundBackofficeCase taxCase, String... keys) {
        for (TaxRefundDocumentSnapshot document : taxCase.verifiedDocuments()) {
            for (String key : keys) {
                String value = document.extractedFields() == null ? null : document.extractedFields().get(key);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return "";
    }

    private String firstFieldOrDefault(TaxRefundBackofficeCase taxCase, String fallback, String... keys) {
        String value = firstField(taxCase, keys);
        return value.isBlank() ? fallback : value;
    }

    private void validateFields(Map<String, String> fields) {
        if (fields == null || fields.size() > 80) {
            throw new IllegalArgumentException("Correction request supports up to 80 fields.");
        }
        fields.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > 80 || value != null && value.length() > 2_000) {
                throw new IllegalArgumentException("Correction request field is invalid.");
            }
        });
    }

    private List<TaxRefundDocumentSnapshot> validatedDocuments(List<TaxRefundDocumentSnapshot> documents) {
        if (documents == null || documents.size() != 3) {
            throw new IllegalArgumentException("Exactly three verified tax documents are required.");
        }
        return documents.stream().map(document -> {
            if (document.contentBase64() == null && document.contentType() == null && document.sha256() == null) {
                // 무중단 배포 중 구버전 거래소가 보낸 메타데이터도 유지한다.
                return new TaxRefundDocumentSnapshot(document.documentId(), document.documentType(), document.fileName(),
                        document.extractedFields(), null, null, null);
            }
            if (document.contentBase64() == null || document.contentType() == null || document.sha256() == null) {
                throw new IllegalArgumentException("Tax document content metadata is incomplete.");
            }
            byte[] content;
            try {
                content = Base64.getDecoder().decode(document.contentBase64());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Tax document content is not valid Base64.", exception);
            }
            if (content.length == 0 || content.length > 10 * 1024 * 1024
                    || !document.sha256().equals(sha256(content))
                    || !matchesContentType(content, document.contentType())) {
                throw new IllegalArgumentException("Tax document content validation failed.");
            }
            return new TaxRefundDocumentSnapshot(document.documentId(), document.documentType(), document.fileName(),
                    document.extractedFields(), null, null, null);
        }).toList();
    }

    private boolean matchesContentType(byte[] content, String contentType) {
        if ("image/png".equals(contentType)) {
            return content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50
                    && content[2] == 0x4e && content[3] == 0x47;
        }
        if ("image/jpeg".equals(contentType)) {
            return content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8;
        }
        return "application/pdf".equals(contentType) && content.length >= 5
                && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Correction request digest failed", exception);
        }
    }
}
