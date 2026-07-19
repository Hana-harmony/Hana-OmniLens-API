package com.hana.omniconnect.common.api;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
