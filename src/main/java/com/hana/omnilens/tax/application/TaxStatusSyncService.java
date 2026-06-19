package com.hana.omnilens.tax.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.hana.omnilens.tax.domain.TaxStatusSyncRequest;
import com.hana.omnilens.tax.domain.TaxStatusSyncResponse;

@Service
public class TaxStatusSyncService {

    private static final String SOURCE = "HANA_TAX_STATUS_RULE_ENGINE";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public TaxStatusSyncService() {
        this(Clock.system(KOREA_ZONE));
    }

    public TaxStatusSyncService(Clock clock) {
        this.clock = clock;
    }

    public TaxStatusSyncResponse sync(TaxStatusSyncRequest request) {
        return new TaxStatusSyncResponse(
                request.caseId(),
                status(request),
                Instant.now(clock),
                SOURCE);
    }

    private String status(TaxStatusSyncRequest request) {
        BigDecimal estimatedRefund = request.estimatedRefundAmount();
        if (estimatedRefund.signum() < 0) {
            return "RECAPTURE_RISK";
        }
        if (estimatedRefund.signum() <= 0) {
            return "NO_REFUNDABLE_PROFIT";
        }
        if (request.advancePaymentRequested() && request.advancePaymentEligible()) {
            return "ADVANCE_PAID";
        }
        if (request.advancePaymentRequested()) {
            return "REFUND_APPROVED";
        }
        if (!request.matchedTradeIds().isEmpty()) {
            return "REFUND_APPROVED";
        }
        return "SYNCED_WITH_HANA";
    }
}
