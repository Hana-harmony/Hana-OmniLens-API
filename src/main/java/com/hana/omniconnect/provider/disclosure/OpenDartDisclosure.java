package com.hana.omniconnect.provider.disclosure;

import java.time.LocalDate;

public record OpenDartDisclosure(
        String receiptNumber,
        String corporationName,
        String reportName,
        LocalDate receivedAt,
        String originalUrl
) {
}
