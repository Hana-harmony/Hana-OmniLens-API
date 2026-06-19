package com.hana.omnilens.tax.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hana.omnilens.tax.domain.TaxRectificationBatchStatusResponse;

@Service
public class TaxRectificationBatchStatusService {

    private static final String SOURCE = "HANA_TAX_RECTIFICATION_BATCH_RULE_ENGINE";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public TaxRectificationBatchStatusService() {
        this(Clock.system(KOREA_ZONE));
    }

    TaxRectificationBatchStatusService(Clock clock) {
        this.clock = clock;
    }

    public TaxRectificationBatchStatusResponse getStatus(int taxYear, int quarter) {
        LocalDate windowStart = filingWindowStart(taxYear, quarter);
        LocalDate windowEnd = filingWindowEnd(taxYear, quarter);
        LocalDate today = LocalDate.now(clock);
        String status = status(today, windowStart, windowEnd);
        int totalCaseCount = totalCaseCount(taxYear, quarter);
        int pendingReviewCaseCount = pendingReviewCaseCount(status, quarter);
        int readyCaseCount = Math.max(0, totalCaseCount - pendingReviewCaseCount);
        return new TaxRectificationBatchStatusResponse(
                "RECT-" + taxYear + "-Q" + quarter,
                taxYear,
                quarter,
                status,
                windowStart,
                windowEnd,
                totalCaseCount,
                readyCaseCount,
                pendingReviewCaseCount,
                requiredNextActions(status, pendingReviewCaseCount),
                Instant.now(clock),
                SOURCE);
    }

    private LocalDate filingWindowStart(int taxYear, int quarter) {
        int month = ((quarter - 1) * 3) + 1;
        return LocalDate.of(taxYear, month, 1);
    }

    private LocalDate filingWindowEnd(int taxYear, int quarter) {
        int month = quarter * 3;
        return YearMonth.of(taxYear, month).atEndOfMonth();
    }

    private String status(LocalDate today, LocalDate windowStart, LocalDate windowEnd) {
        if (today.isBefore(windowStart)) {
            return "SCHEDULED";
        }
        if (!today.isAfter(windowEnd)) {
            return "COLLECTING_CASES";
        }
        if (!today.isAfter(windowEnd.plusDays(15))) {
            return "READY_FOR_REVIEW";
        }
        return "SUBMISSION_PREPARED";
    }

    private int totalCaseCount(int taxYear, int quarter) {
        return Math.max(0, ((taxYear % 100) * 7) + (quarter * 11));
    }

    private int pendingReviewCaseCount(String status, int quarter) {
        if ("SCHEDULED".equals(status)) {
            return 0;
        }
        if ("SUBMISSION_PREPARED".equals(status)) {
            return 0;
        }
        return quarter;
    }

    private List<String> requiredNextActions(String status, int pendingReviewCaseCount) {
        if ("SCHEDULED".equals(status)) {
            return List.of("WAIT_FOR_QUARTER_WINDOW");
        }
        if (pendingReviewCaseCount > 0) {
            return List.of("COMPLETE_MANUAL_TAX_REVIEW", "LOCK_BATCH_AFTER_REVIEW");
        }
        if ("READY_FOR_REVIEW".equals(status)) {
            return List.of("FINALIZE_RECTIFICATION_CLAIM_PACKAGE");
        }
        return List.of("SUBMIT_TO_TAX_AUTHORITY_OUTSIDE_DEMO_SCOPE");
    }
}
