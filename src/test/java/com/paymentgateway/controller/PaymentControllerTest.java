package com.paymentgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.config.TestSecurityConfig;
import com.paymentgateway.exception.FraudDetectedException;
import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.ApiResponse;
import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.dto.PaymentResponse;
import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.PaymentStatus;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(TestSecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    private PaymentRequest validPaymentRequest;
    private PaymentRequest.CardDetails validCardDetails;
    private PaymentResponse mockPaymentResponse;
    private Payment mockPayment;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .active(true)
                .build();

        validCardDetails = PaymentRequest.CardDetails.builder()
                .cardNumber("4111111111111111")
                .expiryMonth("12")
                .expiryYear("2025")
                .cvv("123")
                .cardHolderName("Test User")
                .build();

        validPaymentRequest = PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .merchantReference("TEST-REF-001")
                .description("Test payment")
                .cardDetails(validCardDetails)
                .build();

        mockPaymentResponse = PaymentResponse.builder()
                .paymentId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .gatewayTransactionId("TXN-12345")
                .message("Payment processed successfully")
                .createdAt(LocalDateTime.now())
                .build();

        mockPayment = Payment.builder()
                .id(1L)
                .user(mockUser)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .paymentMethod("CREDIT_CARD")
                .merchantReference("TEST-REF-001")
                .description("Test payment")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void processPayment_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // Given
        when(paymentService.processPayment(any(PaymentRequest.class), eq("testuser")))
                .thenReturn(mockPaymentResponse);

        // When & Then
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPaymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(1))
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.gatewayTransactionId").value("TXN-12345"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void processPayment_FraudDetected_ReturnsBlockedResponse() throws Exception {
        // Given
        when(paymentService.processPayment(any(PaymentRequest.class), eq("testuser")))
                .thenThrow(new FraudDetectedException("Payment blocked due to high fraud risk"));

        // When & Then
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPaymentRequest)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment blocked due to high fraud risk"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void processPayment_InvalidAmount_ReturnsBadRequest() throws Exception {
        // Given - invalid amount
        PaymentRequest invalidRequest = PaymentRequest.builder()
                .amount(new BigDecimal("-10.00")) // Negative amount
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .cardDetails(validCardDetails)
                .build();

        // When & Then
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void processPayment_Unauthenticated_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPaymentRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getPayment_ExistingPayment_ReturnsPayment() throws Exception {
        // Given
        when(paymentService.getPaymentById(1L)).thenReturn(Optional.of(mockPayment));

        // When & Then
        mockMvc.perform(get("/api/payments/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getPayment_NonExistingPayment_ReturnsNotFound() throws Exception {
        // Given
        when(paymentService.getPaymentById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/payments/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserPayments_ExistingUser_ReturnsPayments() throws Exception {
        // Given
        List<Payment> payments = Arrays.asList(mockPayment);
        when(paymentService.getUserPayments("testuser")).thenReturn(payments);

        // When & Then
        mockMvc.perform(get("/api/payments/user/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].amount").value(100.00));
    }

    @Test
    @WithMockUser(username = "testuser")
    void refundPayment_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // Given
        PaymentResponse refundResponse = PaymentResponse.builder()
                .paymentId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.REFUNDED)
                .message("Refund processed successfully")
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.refundPayment(eq(1L), any(BigDecimal.class), anyString()))
                .thenReturn(refundResponse);

        // When & Then
        mockMvc.perform(post("/api/payments/1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"reason\": \"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.message").value("Refund processed successfully"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void refundPayment_PaymentNotFound_ReturnsNotFound() throws Exception {
        // Given
        when(paymentService.refundPayment(eq(999L), any(BigDecimal.class), anyString()))
                .thenThrow(new PaymentException("Payment not found"));

        // When & Then
        mockMvc.perform(post("/api/payments/999/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"reason\": \"Customer request\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void refundPayment_InvalidRefundAmount_ReturnsBadRequest() throws Exception {
        // Given
        when(paymentService.refundPayment(eq(1L), any(BigDecimal.class), anyString()))
                .thenThrow(new PaymentException("Refund amount cannot exceed payment amount"));

        // When & Then
        mockMvc.perform(post("/api/payments/1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 200.00, \"reason\": \"Customer request\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Refund amount cannot exceed payment amount"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelPayment_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/payments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment cancelled successfully"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getPaymentsByStatus_ValidStatus_ReturnsPayments() throws Exception {
        // Given
        List<Payment> payments = Arrays.asList(mockPayment);
        when(paymentService.getPaymentsByStatus(PaymentStatus.COMPLETED)).thenReturn(payments);

        // When & Then
        mockMvc.perform(get("/api/payments/status/COMPLETED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
    }
}