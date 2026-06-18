package com.hana.omnilens.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.common.api.FieldErrorDetail;
import com.hana.omnilens.market.application.StockMasterNotFoundException;
import com.hana.omnilens.security.PartnerAccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(errorCode.status().value(), errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler(StockMasterNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStockMasterNotFound(StockMasterNotFoundException exception) {
        ErrorCode errorCode = ErrorCode.STOCK_NOT_FOUND;
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(errorCode.status().value(), errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler(PartnerAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePartnerAccessDenied(PartnerAccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.PARTNER_ACCESS_DENIED;
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(errorCode.status().value(), errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldErrorDetail> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(errorCode.status().value(), errorCode.code(), errorCode.message()));
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(List<FieldErrorDetail> errors) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(
                        errorCode.status().value(),
                        errorCode.code(),
                        errorCode.message(),
                        errors));
    }
}
