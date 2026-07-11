package com.hana.omnilens.tax.refund;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
        repository.upsert(new TaxRefundBackofficeCase(request.caseId(), request.accountId(), request.userId(), request.taxYear(), request.treatyCountry(), request.estimatedRefundUsd(), request.advancePaymentRequested(), request.advancePaymentEligible(), request.matchedTradeIds(), request.verifiedDocuments(), status, request.requestedAt(), now, submissionStatus, submittedAt));
        return new TaxRefundCaseSyncResponse(request.caseId(), status, now, "HANA_OMNILENS_BACKOFFICE");
    }

    public List<TaxRefundBackofficeCase> list() { return repository.findAll(); }

    public TaxRefundBackofficeCase caseById(String caseId) {
        return repository.findByCaseId(caseId).orElseThrow(() -> new IllegalArgumentException("Tax refund case was not found."));
    }

    public TaxRefundBackofficeCase submitToTaxOffice(String caseId) {
        Instant now = Instant.now();
        caseById(caseId);
        repository.markTaxOfficeSubmitted(caseId, now);
        return caseById(caseId);
    }

    public Map<String, String> initialCorrectionFields(String caseId) {
        TaxRefundBackofficeCase taxCase = caseById(caseId);
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("claimantName", firstField(taxCase, "name", "holder_name", "applicant_name"));
        fields.put("claimantResidence", firstField(taxCase, "residence_country", "country", "country_code", taxCase.treatyCountry()));
        fields.put("taxYear", String.valueOf(taxCase.taxYear()));
        fields.put("estimatedRefundUsd", taxCase.estimatedRefundUsd());
        fields.put("accountId", taxCase.accountId());
        fields.put("caseId", taxCase.caseId());
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
}
