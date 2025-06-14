package com.paymentgateway.controller;

import com.paymentgateway.exception.FraudDetectedException;
import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.ApiResponse;
import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.dto.PaymentResponse;
import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.PaymentStatus;
import com.paymentgateway.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            log.info("Processing payment for user: {}, amount: {}", username, request.getAmount());

            PaymentResponse response = paymentService.processPayment(request, username);

            log.info("Payment processed successfully: {}", response.getPaymentId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Payment processed successfully", response));

        } catch (FraudDetectedException e) {
            log.warn("🚨 PAYMENT BLOCKED - FRAUD DETECTED: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("FRAUD DETECTED: " + e.getMessage()));
        } catch (PaymentException e) {
            log.warn("Payment processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during payment processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment processing failed"));
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentSummary>> getPayment(@PathVariable Long paymentId) {
        try {
            Optional<Payment> payment = paymentService.getPaymentById(paymentId);

            if (payment.isPresent()) {
                PaymentSummary summary = createPaymentSummary(payment.get());
                return ResponseEntity.ok(ApiResponse.success(summary));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Payment not found"));
            }

        } catch (Exception e) {
            log.error("Error retrieving payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error retrieving payment"));
        }
    }

    @GetMapping("/user/me")
    public ResponseEntity<ApiResponse<List<PaymentSummary>>> getUserPayments(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            List<Payment> payments = paymentService.getUserPayments(username);

            // Convert to simplified DTOs to avoid JSON circular references
            List<PaymentSummary> paymentSummaries = payments.stream()
                    .map(this::createPaymentSummary)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(paymentSummaries));

        } catch (PaymentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving user payments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error retrieving payments"));
        }
    }

    @GetMapping("/user/me/paginated")
    public ResponseEntity<ApiResponse<Page<PaymentSummary>>> getUserPaymentsPaginated(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            Page<Payment> payments = paymentService.getUserPayments(username, pageable);

            // Convert to simplified DTOs
            Page<PaymentSummary> paymentSummaries = payments.map(this::createPaymentSummary);

            return ResponseEntity.ok(ApiResponse.success(paymentSummaries));

        } catch (PaymentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving user payments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error retrieving payments"));
        }
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @PathVariable Long paymentId,
            @RequestBody Map<String, Object> refundRequest) {

        try {
            BigDecimal refundAmount = new BigDecimal(refundRequest.get("amount").toString());
            String reason = refundRequest.getOrDefault("reason", "Refund requested").toString();

            PaymentResponse response = paymentService.refundPayment(paymentId, refundAmount, reason);

            log.info("Refund processed successfully for payment: {}", paymentId);
            return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", response));

        } catch (PaymentException e) {
            log.warn("Refund processing failed for payment: {}, reason: {}", paymentId, e.getMessage());

            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage()));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during refund processing for payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Refund processing failed"));
        }
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelPayment(
            @PathVariable Long paymentId,
            @RequestBody Map<String, String> cancelRequest) {

        try {
            String reason = cancelRequest.getOrDefault("reason", "Payment cancelled by user");

            paymentService.cancelPayment(paymentId, reason);

            log.info("Payment cancelled successfully: {}", paymentId);
            return ResponseEntity.ok(ApiResponse.success("Payment cancelled successfully", null));

        } catch (PaymentException e) {
            log.warn("Payment cancellation failed for payment: {}, reason: {}", paymentId, e.getMessage());

            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(e.getMessage()));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during payment cancellation: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Payment cancellation failed"));
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PaymentSummary>>> getPaymentsByStatus(@PathVariable String status) {
        try {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
            List<Payment> payments = paymentService.getPaymentsByStatus(paymentStatus);

            List<PaymentSummary> paymentSummaries = payments.stream()
                    .map(this::createPaymentSummary)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(paymentSummaries));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid payment status: " + status));
        } catch (Exception e) {
            log.error("Error retrieving payments by status: {}", status, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error retrieving payments"));
        }
    }

    @GetMapping("/analytics/total")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalAmount(
            Authentication authentication,
            @RequestParam(required = false) String status) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            PaymentStatus paymentStatus = status != null ?
                    PaymentStatus.valueOf(status.toUpperCase()) : PaymentStatus.COMPLETED;

            BigDecimal total = paymentService.getTotalAmountByUser(username, paymentStatus);

            return ResponseEntity.ok(ApiResponse.success(total));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid payment status: " + status));
        } catch (Exception e) {
            log.error("Error calculating total amount", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error calculating total amount"));
        }
    }

    @GetMapping("/analytics/date-range")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalAmountByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        try {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);

            BigDecimal total = paymentService.getTotalSuccessfulPaymentsBetween(start, end);

            return ResponseEntity.ok(ApiResponse.success(total));

        } catch (Exception e) {
            log.error("Error calculating total amount by date range", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid date format or range"));
        }
    }

    @GetMapping("/high-risk")
    public ResponseEntity<ApiResponse<List<PaymentSummary>>> getHighRiskPayments(
            @RequestParam(required = false) BigDecimal threshold) {

        try {
            List<Payment> payments = paymentService.getHighRiskPayments(threshold);

            List<PaymentSummary> paymentSummaries = payments.stream()
                    .map(this::createPaymentSummary)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(paymentSummaries));

        } catch (Exception e) {
            log.error("Error retrieving high-risk payments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error retrieving high-risk payments"));
        }
    }

    // Helper method to create simplified payment summary (avoids JSON circular references)
    private PaymentSummary createPaymentSummary(Payment payment) {
        return PaymentSummary.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .merchantReference(payment.getMerchantReference())
                .description(payment.getDescription())
                .fraudScore(payment.getFraudScore())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .userId(payment.getUser().getId())
                .username(payment.getUser().getUsername())
                .build();
    }

    // Simple DTO to avoid circular references
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentSummary {
        private Long id;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private String paymentMethod;
        private String merchantReference;
        private String description;
        private BigDecimal fraudScore;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long userId;
        private String username;
    }
}