package com.paymentgateway.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method is too long")
    private String paymentMethod;

    @Size(max = 100, message = "Merchant reference is too long")
    private String merchantReference;

    @Size(max = 500, message = "Description is too long")
    private String description;

    // Payment method specific fields
    private CardDetails cardDetails;
    private BankDetails bankDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetails {
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "\\d{13,19}", message = "Invalid card number format")
        private String cardNumber;

        @NotBlank(message = "Expiry month is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month")
        private String expiryMonth;

        @NotBlank(message = "Expiry year is required")
        @Pattern(regexp = "^\\d{4}$", message = "Invalid expiry year")
        private String expiryYear;

        @NotBlank(message = "CVV is required")
        @Pattern(regexp = "^\\d{3,4}$", message = "Invalid CVV")
        private String cvv;

        @NotBlank(message = "Card holder name is required")
        @Size(max = 100, message = "Card holder name is too long")
        private String cardHolderName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankDetails {
        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotBlank(message = "Routing number is required")
        private String routingNumber;

        @NotBlank(message = "Account holder name is required")
        private String accountHolderName;
    }
}