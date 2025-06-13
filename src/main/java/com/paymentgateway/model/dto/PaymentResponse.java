package com.paymentgateway.model.dto;

import com.paymentgateway.model.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long paymentId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethod;
    private String merchantReference;
    private String description;
    private BigDecimal fraudScore;
    private String gatewayTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;

    // Success response
    public static PaymentResponse success(Long paymentId, BigDecimal amount,
                                          String currency, String gatewayTransactionId) {
        return PaymentResponse.builder()
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.COMPLETED)
                .gatewayTransactionId(gatewayTransactionId)
                .createdAt(LocalDateTime.now())
                .message("Payment processed successfully")
                .build();
    }

    // Error response
    public static PaymentResponse error(String message) {
        return PaymentResponse.builder()
                .status(PaymentStatus.FAILED)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}