package com.hana.omnilens.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.tax.domain.TaxRectificationBatchStatusResponse;

class TaxRectificationBatchStatusServiceTest {

    @Test
    void statusIsScheduledBeforeQuarterWindow() {
        TaxRectificationBatchStatusService service = serviceAt("2026-03-31T15:00:00Z");

        TaxRectificationBatchStatusResponse response = service.getStatus(2026, 2);

        assertThat(response.batchId()).isEqualTo("RECT-2026-Q2");
        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.filingWindowStart()).hasToString("2026-04-01");
        assertThat(response.filingWindowEnd()).hasToString("2026-06-30");
        assertThat(response.requiredNextActions()).containsExactly("WAIT_FOR_QUARTER_WINDOW");
    }

    @Test
    void statusCollectsCasesDuringQuarterWindow() {
        TaxRectificationBatchStatusService service = serviceAt("2026-04-10T00:00:00Z");

        TaxRectificationBatchStatusResponse response = service.getStatus(2026, 2);

        assertThat(response.status()).isEqualTo("COLLECTING_CASES");
        assertThat(response.totalCaseCount()).isEqualTo(204);
        assertThat(response.readyCaseCount()).isEqualTo(202);
        assertThat(response.pendingReviewCaseCount()).isEqualTo(2);
        assertThat(response.requiredNextActions()).containsExactly(
                "COMPLETE_MANUAL_TAX_REVIEW",
                "LOCK_BATCH_AFTER_REVIEW");
    }

    @Test
    void statusIsSubmissionPreparedAfterReviewBuffer() {
        TaxRectificationBatchStatusService service = serviceAt("2026-07-20T00:00:00Z");

        TaxRectificationBatchStatusResponse response = service.getStatus(2026, 2);

        assertThat(response.status()).isEqualTo("SUBMISSION_PREPARED");
        assertThat(response.pendingReviewCaseCount()).isZero();
        assertThat(response.requiredNextActions()).containsExactly("SUBMIT_TO_TAX_AUTHORITY_OUTSIDE_DEMO_SCOPE");
        assertThat(response.source()).isEqualTo("HANA_TAX_RECTIFICATION_BATCH_RULE_ENGINE");
    }

    private TaxRectificationBatchStatusService serviceAt(String instant) {
        return new TaxRectificationBatchStatusService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
