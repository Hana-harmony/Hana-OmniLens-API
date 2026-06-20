package com.hana.omnilens.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_002", "Request validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "Resource not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "Internal server error"),
    MARKET_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_002", "Market data provider is unavailable"),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "AUTH_001", "Invalid API key"),
    API_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_002", "API key hash is not configured"),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "MARKET_001", "Stock not found"),
    PARTNER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_005", "Partner access denied");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
