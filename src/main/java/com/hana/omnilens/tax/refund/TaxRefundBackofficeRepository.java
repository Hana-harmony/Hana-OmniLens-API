package com.hana.omnilens.tax.refund;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaxRefundBackofficeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaxRefundBackofficeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void upsert(TaxRefundBackofficeCase taxCase) {
        int updated = jdbcTemplate.update(
                "UPDATE tax_refund_backoffice_cases SET account_id = ?, user_id = ?, tax_year = ?, treaty_country = ?, estimated_refund_usd = ?, advance_payment_requested = ?, advance_payment_eligible = ?, matched_trade_ids_json = ?, verified_documents_json = ?, status = ?, requested_at = ?, synced_at = ? WHERE case_id = ?",
                taxCase.accountId(), taxCase.userId(), taxCase.taxYear(), taxCase.treatyCountry(), taxCase.estimatedRefundUsd(),
                taxCase.advancePaymentRequested(), taxCase.advancePaymentEligible(), json(taxCase.matchedTradeIds()), json(taxCase.verifiedDocuments()), taxCase.status(),
                Timestamp.from(taxCase.requestedAt()), Timestamp.from(taxCase.syncedAt()), taxCase.caseId());
        if (updated > 0) return;
        jdbcTemplate.update(
                "INSERT INTO tax_refund_backoffice_cases (case_id, account_id, user_id, tax_year, treaty_country, estimated_refund_usd, advance_payment_requested, advance_payment_eligible, matched_trade_ids_json, verified_documents_json, status, requested_at, synced_at, tax_office_submission_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                taxCase.caseId(), taxCase.accountId(), taxCase.userId(), taxCase.taxYear(), taxCase.treatyCountry(), taxCase.estimatedRefundUsd(),
                taxCase.advancePaymentRequested(), taxCase.advancePaymentEligible(), json(taxCase.matchedTradeIds()), json(taxCase.verifiedDocuments()), taxCase.status(),
                Timestamp.from(taxCase.requestedAt()), Timestamp.from(taxCase.syncedAt()), taxCase.taxOfficeSubmissionStatus());
    }

    public List<TaxRefundBackofficeCase> findAll() {
        return jdbcTemplate.query(select() + " ORDER BY synced_at DESC", (resultSet, rowNumber) -> taxCase(resultSet));
    }

    public Optional<TaxRefundBackofficeCase> findByCaseId(String caseId) {
        return jdbcTemplate.query(select() + " WHERE case_id = ?", (resultSet, rowNumber) -> taxCase(resultSet), caseId)
                .stream()
                .findFirst();
    }

    public void savePreparedCorrection(String caseId, Map<String, String> fields, byte[] pdf, String sha256, String administratorId, Instant preparedAt) {
        jdbcTemplate.update(
                "UPDATE tax_refund_backoffice_cases SET correction_fields_json = ?, correction_pdf = ?, correction_pdf_sha256 = ?, correction_request_status = ?, correction_prepared_by_user_id = ?, correction_prepared_at = ? WHERE case_id = ?",
                jsonMap(fields), pdf, sha256, "PREPARED", administratorId, Timestamp.from(preparedAt), caseId);
    }

    public Optional<byte[]> findPreparedCorrectionPdf(String caseId) {
        return jdbcTemplate.query(
                "SELECT correction_pdf FROM tax_refund_backoffice_cases WHERE case_id = ? AND correction_pdf IS NOT NULL",
                (resultSet, rowNumber) -> resultSet.getBytes("correction_pdf"), caseId).stream().findFirst();
    }

    public void approve(String caseId, String administratorId, Instant approvedAt) {
        jdbcTemplate.update(
                "UPDATE tax_refund_backoffice_cases SET status = ?, tax_office_submission_status = ?, tax_office_submitted_at = ?, correction_request_status = ?, approved_by_user_id = ?, approved_at = ? WHERE case_id = ?",
                "REFUND_APPROVED", "SUBMITTED_TO_NTS", Timestamp.from(approvedAt), "APPROVED", administratorId,
                Timestamp.from(approvedAt), caseId);
    }

    private String select() {
        return "SELECT case_id, account_id, user_id, tax_year, treaty_country, estimated_refund_usd, advance_payment_requested, advance_payment_eligible, matched_trade_ids_json, verified_documents_json, status, requested_at, synced_at, tax_office_submission_status, tax_office_submitted_at, correction_request_status, correction_pdf_sha256, correction_prepared_at, approved_at FROM tax_refund_backoffice_cases";
    }

    private TaxRefundBackofficeCase taxCase(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TaxRefundBackofficeCase(
                resultSet.getString("case_id"), resultSet.getString("account_id"), resultSet.getString("user_id"), resultSet.getInt("tax_year"), resultSet.getString("treaty_country"), resultSet.getBigDecimal("estimated_refund_usd").toPlainString(), resultSet.getBoolean("advance_payment_requested"), resultSet.getBoolean("advance_payment_eligible"), list(resultSet.getString("matched_trade_ids_json")), documents(resultSet.getString("verified_documents_json")), resultSet.getString("status"), instant(resultSet.getTimestamp("requested_at")), instant(resultSet.getTimestamp("synced_at")), resultSet.getString("tax_office_submission_status"), nullableInstant(resultSet.getTimestamp("tax_office_submitted_at")), resultSet.getString("correction_request_status"), resultSet.getString("correction_pdf_sha256"), nullableInstant(resultSet.getTimestamp("correction_prepared_at")), nullableInstant(resultSet.getTimestamp("approved_at")));
    }

    private String json(List<?> values) {
        try { return objectMapper.writeValueAsString(values == null ? List.of() : values); } catch (Exception exception) { throw new IllegalStateException("Tax trade serialization failed", exception); }
    }
    private String jsonMap(Map<String, String> values) {
        try { return objectMapper.writeValueAsString(values == null ? Map.of() : values); } catch (Exception exception) { throw new IllegalStateException("Tax correction field serialization failed", exception); }
    }
    private List<String> list(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { throw new IllegalStateException("Tax trade parsing failed", exception); }
    }
    private List<TaxRefundDocumentSnapshot> documents(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { throw new IllegalStateException("Tax document parsing failed", exception); }
    }
    private Instant instant(Timestamp value) { return value.toInstant(); }
    private Instant nullableInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
