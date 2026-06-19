package com.hana.omnilens.tax.domain;

import java.time.Instant;
import java.util.List;

public record TaxTreatyCaseClassificationResponse(
        String caseId,
        String treatyCaseType,
        boolean eligibleForTreatyBenefit,
        List<String> classificationReasons,
        List<String> requiredNextActions,
        Instant classifiedAt,
        String modelVersion,
        String source
) {
}
