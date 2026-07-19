package com.hana.omnilens.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_002", "Request validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_003", "Resource not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "Internal server error"),
    MARKET_DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_002", "Market data provider is unavailable"),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "AUTH_001", "Invalid API key"),
    API_KEY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_002", "No active partner API key is configured"),
	SECURITY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_008", "Security service is unavailable"),
    PORTAL_AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "Portal authentication is required"),
    PORTAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_007", "Portal access denied"),
    PORTAL_USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "PORTAL_001", "Portal username already exists"),
    PORTAL_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "PORTAL_002", "Invalid username or password"),
    API_KEY_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "PORTAL_003", "API key application not found"),
    API_KEY_APPLICATION_INVALID_STATE(HttpStatus.CONFLICT, "PORTAL_004", "API key application cannot be processed"),
    PORTAL_SECURITY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "PORTAL_005", "Portal security configuration is unavailable"),
    PORTAL_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PORTAL_006", "Password confirmation does not match"),
    PORTAL_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "PORTAL_007", "Password change is required"),
    PORTAL_PASSWORD_REUSE(HttpStatus.BAD_REQUEST, "PORTAL_008", "New password must differ from the current password"),
    PORTAL_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "PORTAL_009", "Too many authentication attempts"),
    TAX_CORRECTION_NOT_PREPARED(HttpStatus.CONFLICT, "TAX_001", "Correction request must be prepared before approval"),
    TAX_CORRECTION_ALREADY_APPROVED(HttpStatus.CONFLICT, "TAX_002", "Tax refund case is already approved"),
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
