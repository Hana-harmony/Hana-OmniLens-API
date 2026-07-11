package com.hana.omnilens.market.domain;

import java.util.List;
import java.util.Set;

public final class GlobalPeerContractPolicy {

    private static final Set<String> ALLOWED_SOURCES = Set.of(
            "HANNAH_GLOBAL_PEER_HYBRID_RANKER");

    private static final Set<String> ALLOWED_DIMENSIONS = Set.of(
            "overall_business",
            "semiconductor",
            "semiconductor_ds",
            "memory",
            "foundry",
            "consumer_electronics",
            "software_platform",
            "financial_services",
            "payments",
            "biotechnology",
            "drug_delivery",
            "battery",
            "automotive",
            "telecommunications",
            "energy",
            "materials",
            "industrial",
            "commerce",
            "media",
            "operational_scale");

    private static final Set<String> ALLOWED_ICON_KEYS = Set.of(
            "memory",
            "foundry",
            "ai",
            "ecosystem",
            "semiconductor",
            "consumer_electronics",
            "software_platform",
            "financial_services",
            "payments",
            "biotechnology",
            "drug_delivery",
            "battery",
            "automotive",
            "telecommunications",
            "energy",
            "materials",
            "industrial",
            "commerce",
            "media",
            "global_business",
            "operational_scale");

    private GlobalPeerContractPolicy() {
    }

    public static String requireDimension(String value) {
        return requireAllowed("dimension", value, ALLOWED_DIMENSIONS);
    }

    public static String requireIconKey(String value) {
        return requireAllowed("iconKey", value, ALLOWED_ICON_KEYS);
    }

    public static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static <T> List<T> copyRequiredList(String field, List<T> values) {
        if (values == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return List.copyOf(values);
    }

    public static void validateCardinality(
            String source,
            List<?> comparisons,
            List<?> keyStrengths) {
        requireAllowed("source", source, ALLOWED_SOURCES);
        validateStrictCardinality(comparisons, keyStrengths);
    }

    public static void validateStrictCardinality(
            List<?> comparisons,
            List<?> keyStrengths) {
        if (comparisons.isEmpty() || comparisons.size() > 3) {
            throw new IllegalArgumentException("comparisons size must be between 1 and 3");
        }
        if (keyStrengths.size() != 4) {
            throw new IllegalArgumentException("keyStrengths size must be 4");
        }
    }

    private static String requireAllowed(String field, String value, Set<String> allowedValues) {
        String normalized = requireText(field, value);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(field + " is not allowed: " + normalized);
        }
        return normalized;
    }
}
