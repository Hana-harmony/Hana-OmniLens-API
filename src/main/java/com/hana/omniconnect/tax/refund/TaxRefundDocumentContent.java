package com.hana.omniconnect.tax.refund;

public record TaxRefundDocumentContent(
        String documentId,
        String fileName,
        String contentType,
        byte[] content
) {
}
