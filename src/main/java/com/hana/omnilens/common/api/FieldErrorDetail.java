package com.hana.omnilens.common.api;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
