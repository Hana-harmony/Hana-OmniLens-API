package com.hana.omniconnect.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.common.api.FieldErrorDetail;
import com.hana.omniconnect.market.application.StockMasterNotFoundException;
import com.hana.omniconnect.security.PartnerAccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.error(errorCode.status().value(), errorCode.code(), errorCode.message()));
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        List<FieldErrorDetail> errors = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                result.getMethodParameter().getParameterName() == null
                                        ? "request"
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationResponse(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandled(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Unhandled API exception", exception);
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
