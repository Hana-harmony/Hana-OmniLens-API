package com.hana.omnilens.config;

import java.net.URI;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.hana.omnilens.market.application.StockMasterNotFoundException;
import com.hana.omnilens.security.PartnerAccessDeniedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final URI VALIDATION_ERROR_TYPE = URI.create("https://hana-omnilens-api/errors/validation");
    private static final URI STOCK_NOT_FOUND_TYPE = URI.create("https://hana-omnilens-api/errors/stock-not-found");
    private static final URI PARTNER_ACCESS_DENIED_TYPE =
            URI.create("https://hana-omnilens-api/errors/partner-access-denied");

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class
    })
    ProblemDetail handleValidationException(Exception exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(VALIDATION_ERROR_TYPE);
        problemDetail.setTitle("Invalid request");
        problemDetail.setDetail("Request validation failed");
        return problemDetail;
    }

    @ExceptionHandler(StockMasterNotFoundException.class)
    ProblemDetail handleStockMasterNotFoundException(StockMasterNotFoundException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setType(STOCK_NOT_FOUND_TYPE);
        problemDetail.setTitle("Stock not found");
        problemDetail.setDetail("Supported stock master row was not found");
        problemDetail.setProperty("stockCode", exception.stockCode());
        return problemDetail;
    }

    @ExceptionHandler(PartnerAccessDeniedException.class)
    ProblemDetail handlePartnerAccessDeniedException(PartnerAccessDeniedException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setType(PARTNER_ACCESS_DENIED_TYPE);
        problemDetail.setTitle("Partner access denied");
        problemDetail.setDetail("Authenticated partner cannot access requested partner resource");
        problemDetail.setProperty("requestedPartnerId", exception.requestedPartnerId());
        return problemDetail;
    }
}
