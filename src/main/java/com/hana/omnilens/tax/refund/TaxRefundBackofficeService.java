package com.hana.omnilens.tax.refund;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

@Service
public class TaxRefundBackofficeService {

    private final TaxRefundBackofficeRepository repository;

    public TaxRefundBackofficeService(TaxRefundBackofficeRepository repository) { this.repository = repository; }

    public TaxRefundCaseSyncResponse sync(TaxRefundCaseSyncRequest request) {
        Instant now = Instant.now();
        TaxRefundBackofficeCase current = repository.findByCaseId(request.caseId()).orElse(null);
        String status = current == null ? "SYNCED_WITH_HANA" : current.status();
        String submissionStatus = current == null ? "NOT_SUBMITTED" : current.taxOfficeSubmissionStatus();
        Instant submittedAt = current == null ? null : current.taxOfficeSubmittedAt();
        repository.upsert(new TaxRefundBackofficeCase(request.caseId(), request.accountId(), request.userId(), request.taxYear(), request.treatyCountry(), request.estimatedRefundUsd(), request.advancePaymentRequested(), request.advancePaymentEligible(), request.matchedTradeIds(), request.verifiedDocuments(), status, request.requestedAt(), now, submissionStatus, submittedAt,
                current == null ? "NOT_PREPARED" : current.correctionRequestStatus(),
                current == null ? null : current.correctionPdfSha256(),
                current == null ? null : current.correctionPreparedAt(),
                current == null ? null : current.approvedAt()));
        return new TaxRefundCaseSyncResponse(request.caseId(), status, now, "HANA_OMNILENS_BACKOFFICE");
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
        return caseById(caseId);
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
        fields.put("claimantResidence", firstFieldOrDefault(taxCase, taxCase.treatyCountry(), "residency_country", "residence_country", "country"));
        fields.put("residencyCountryCode", firstFieldOrDefault(taxCase, taxCase.treatyCountry(), "residency_country_code", "country_code"));
        fields.put("taxpayerIdentificationNumber", firstField(taxCase, "tin", "taxpayer_identification_number"));
        fields.put("claimantAddress", firstField(taxCase, "address", "residence_address"));
        fields.put("apostilleCertificateNumber", firstField(taxCase, "certificate_number"));
        fields.put("taxYear", String.valueOf(taxCase.taxYear()));
        fields.put("estimatedRefundUsd", taxCase.estimatedRefundUsd());
        fields.put("accountId", taxCase.accountId());
        fields.put("caseId", taxCase.caseId());
        for (TaxRefundDocumentSnapshot document : taxCase.verifiedDocuments()) {
            if (document.extractedFields() == null) continue;
            document.extractedFields().forEach((key, value) -> {
                if (key != null && value != null && !value.isBlank()) fields.putIfAbsent(key, value);
            });
        }
        return fields;
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

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Correction request digest failed", exception);
        }
    }
}
