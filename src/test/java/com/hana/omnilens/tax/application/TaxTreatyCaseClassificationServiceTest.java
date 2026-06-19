package com.hana.omnilens.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationRequest;
import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationResponse;

class TaxTreatyCaseClassificationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-19T00:00:00Z"),
            ZoneOffset.UTC);

    private final TaxTreatyCaseClassificationService service =
            new TaxTreatyCaseClassificationService(FIXED_CLOCK);

    @Test
    void classifiesHongKongListedStockCase01() {
        TaxTreatyCaseClassificationResponse response = service.classify(request(
                "HK",
                "HK",
                true,
                "0.20",
                true,
                true));

        assertThat(response.caseId()).isEqualTo("TAX-CASE-1");
        assertThat(response.treatyCaseType()).isEqualTo("CASE_01");
        assertThat(response.eligibleForTreatyBenefit()).isTrue();
        assertThat(response.classificationReasons()).containsExactly("KR_HK_LISTED_STOCK_TREATY_CASE_01");
        assertThat(response.requiredNextActions()).containsExactly("PROCEED_TAX_REFUND_STATUS_SYNC");
        assertThat(response.classifiedAt()).isEqualTo(Instant.parse("2026-06-19T00:00:00Z"));
        assertThat(response.modelVersion()).isEqualTo("kr-hk-treaty-case-classifier-v1");
        assertThat(response.source()).isEqualTo("HANA_TAX_TREATY_RULE_ENGINE");
    }

    @Test
    void classifiesManualReviewWhenOwnershipRateExceedsTreatyBoundary() {
        TaxTreatyCaseClassificationResponse response = service.classify(request(
                "HK",
                "HK",
                true,
                "25.00",
                true,
                true));

        assertThat(response.treatyCaseType()).isEqualTo("CASE_REVIEW_REQUIRED");
        assertThat(response.eligibleForTreatyBenefit()).isFalse();
        assertThat(response.classificationReasons()).containsExactly("OWNERSHIP_RATE_25_PERCENT_OR_MORE");
        assertThat(response.requiredNextActions()).containsExactly("MANUAL_TAX_LEGAL_REVIEW");
    }

    @Test
    void requiresDocumentVerificationBeforeCase01() {
        TaxTreatyCaseClassificationResponse response = service.classify(request(
                "HK",
                "HK",
                true,
                "0.20",
                false,
                false));

        assertThat(response.treatyCaseType()).isEqualTo("CASE_REVIEW_REQUIRED");
        assertThat(response.classificationReasons())
                .containsExactly("RESIDENCE_CERTIFICATE_NOT_VERIFIED", "TREATY_APPLICATION_NOT_VERIFIED");
        assertThat(response.requiredNextActions())
                .containsExactly("VERIFY_RESIDENCE_CERTIFICATE", "VERIFY_TREATY_APPLICATION");
    }

    private TaxTreatyCaseClassificationRequest request(
            String treatyCountry,
            String investorResidencyCountry,
            boolean allListedMarketTrade,
            String maxOwnershipRatePercent,
            boolean residenceCertificateVerified,
            boolean treatyApplicationVerified
    ) {
        return new TaxTreatyCaseClassificationRequest(
                "TAX-CASE-1",
                2026,
                treatyCountry,
                investorResidencyCountry,
                List.of("DIVIDEND", "SELL"),
                allListedMarketTrade,
                maxOwnershipRatePercent,
                residenceCertificateVerified,
                treatyApplicationVerified,
                Instant.parse("2026-06-18T06:00:00Z"));
    }
}
