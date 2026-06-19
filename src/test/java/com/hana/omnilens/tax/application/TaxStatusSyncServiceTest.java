package com.hana.omnilens.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.tax.domain.TaxStatusSyncRequest;
import com.hana.omnilens.tax.domain.TaxStatusSyncResponse;

class TaxStatusSyncServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-19T00:00:00Z"),
            ZoneOffset.UTC);

    private final TaxStatusSyncService service = new TaxStatusSyncService(FIXED_CLOCK);

    @Test
    void syncApprovesAdvancePaymentWhenRefundAndEligibilityExist() {
        TaxStatusSyncResponse response = service.sync(request("1.40", true, true, List.of("TRD-1")));

        assertThat(response.caseId()).isEqualTo("TAX-CASE-1");
        assertThat(response.status()).isEqualTo("ADVANCE_PAID");
        assertThat(response.syncedAt()).isEqualTo(Instant.parse("2026-06-19T00:00:00Z"));
        assertThat(response.source()).isEqualTo("HANA_TAX_STATUS_RULE_ENGINE");
    }

    @Test
    void syncReturnsNoRefundableProfitWhenRefundAmountIsZero() {
        TaxStatusSyncResponse response = service.sync(request("0.00", true, true, List.of("TRD-1")));

        assertThat(response.status()).isEqualTo("NO_REFUNDABLE_PROFIT");
    }

    @Test
    void syncFlagsRecaptureRiskWhenRefundEstimateIsNegative() {
        TaxStatusSyncResponse response = service.sync(request("-1.40", true, true, List.of("TRD-1")));

        assertThat(response.status()).isEqualTo("RECAPTURE_RISK");
    }

    @Test
    void syncApprovesRefundWhenMatchedTradesHaveRefundWithoutAdvancePayment() {
        TaxStatusSyncResponse response = service.sync(request("1.40", false, false, List.of("TRD-1")));

        assertThat(response.status()).isEqualTo("REFUND_APPROVED");
    }

    private TaxStatusSyncRequest request(
            String estimatedRefundUsd,
            boolean advancePaymentRequested,
            boolean advancePaymentEligible,
            List<String> matchedTradeIds
    ) {
        return new TaxStatusSyncRequest(
                "TAX-CASE-1",
                "ACC-ABC123456789",
                "USR-ABC123456789",
                2026,
                "US",
                estimatedRefundUsd,
                advancePaymentRequested,
                advancePaymentEligible,
                matchedTradeIds,
                Instant.parse("2026-06-18T06:00:00Z"));
    }
}
