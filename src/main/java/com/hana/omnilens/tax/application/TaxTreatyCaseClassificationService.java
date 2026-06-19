package com.hana.omnilens.tax.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationRequest;
import com.hana.omnilens.tax.domain.TaxTreatyCaseClassificationResponse;

@Service
public class TaxTreatyCaseClassificationService {

    private static final String MODEL_VERSION = "kr-hk-treaty-case-classifier-v1";
    private static final String SOURCE = "HANA_TAX_TREATY_RULE_ENGINE";
    private static final BigDecimal CASE_01_MAX_OWNERSHIP_RATE = new BigDecimal("25.0");
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public TaxTreatyCaseClassificationService() {
        this(Clock.system(KOREA_ZONE));
    }

    TaxTreatyCaseClassificationService(Clock clock) {
        this.clock = clock;
    }

    public TaxTreatyCaseClassificationResponse classify(TaxTreatyCaseClassificationRequest request) {
        List<String> reasons = classificationReasons(request);
        boolean eligible = reasons.isEmpty();
        return new TaxTreatyCaseClassificationResponse(
                request.caseId(),
                eligible ? "CASE_01" : "CASE_REVIEW_REQUIRED",
                eligible,
                eligible ? List.of("KR_HK_LISTED_STOCK_TREATY_CASE_01") : reasons,
                requiredNextActions(reasons),
                Instant.now(clock),
                MODEL_VERSION,
                SOURCE);
    }

    private List<String> classificationReasons(TaxTreatyCaseClassificationRequest request) {
        List<String> reasons = new ArrayList<>();
        if (!"HK".equals(request.treatyCountry()) || !"HK".equals(request.investorResidencyCountry())) {
            reasons.add("NON_HK_TREATY_RESIDENCY");
        }
        if (!request.allListedMarketTrade()) {
            reasons.add("NON_LISTED_MARKET_TRADE");
        }
        if (new BigDecimal(request.maxOwnershipRatePercent()).compareTo(CASE_01_MAX_OWNERSHIP_RATE) >= 0) {
            reasons.add("OWNERSHIP_RATE_25_PERCENT_OR_MORE");
        }
        if (!request.residenceCertificateVerified()) {
            reasons.add("RESIDENCE_CERTIFICATE_NOT_VERIFIED");
        }
        if (!request.treatyApplicationVerified()) {
            reasons.add("TREATY_APPLICATION_NOT_VERIFIED");
        }
        if (request.incomeTypes().stream().anyMatch(incomeType -> !"DIVIDEND".equals(incomeType)
                && !"SELL".equals(incomeType))) {
            reasons.add("UNSUPPORTED_INCOME_TYPE");
        }
        return reasons;
    }

    private List<String> requiredNextActions(List<String> reasons) {
        if (reasons.isEmpty()) {
            return List.of("PROCEED_TAX_REFUND_STATUS_SYNC");
        }
        List<String> actions = new ArrayList<>();
        if (reasons.contains("RESIDENCE_CERTIFICATE_NOT_VERIFIED")) {
            actions.add("VERIFY_RESIDENCE_CERTIFICATE");
        }
        if (reasons.contains("TREATY_APPLICATION_NOT_VERIFIED")) {
            actions.add("VERIFY_TREATY_APPLICATION");
        }
        if (reasons.contains("OWNERSHIP_RATE_25_PERCENT_OR_MORE")
                || reasons.contains("NON_LISTED_MARKET_TRADE")
                || reasons.contains("NON_HK_TREATY_RESIDENCY")) {
            actions.add("MANUAL_TAX_LEGAL_REVIEW");
        }
        if (actions.isEmpty()) {
            actions.add("MANUAL_TAX_REVIEW");
        }
        return actions;
    }
}
