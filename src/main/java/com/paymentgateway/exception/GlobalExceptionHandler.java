package com.paymentgateway.exception;

import com.paymentgateway.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentException(PaymentException ex, WebRequest request) {
        log.warn("Payment exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(FraudDetectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFraudDetectedException(FraudDetectedException ex, WebRequest request) {
        log.warn("Fraud detected: {}", ex.getMessage());

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("fraudScore", ex.getFraudScore());
        errorDetails.put("fraudDetails", ex.getFraudDetails());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errors.toString()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid username or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request: " + ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}