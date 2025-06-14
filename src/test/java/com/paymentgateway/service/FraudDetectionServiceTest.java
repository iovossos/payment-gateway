package com.paymentgateway.service;

import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.PaymentStatus;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private User testUser;
    private PaymentRequest lowAmountRequest;
    private PaymentRequest highAmountRequest;
    private PaymentRequest veryHighAmountRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .active(true)
                .build();

        lowAmountRequest = PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .build();

        highAmountRequest = PaymentRequest.builder()
                .amount(new BigDecimal("15000.00"))
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .build();

        veryHighAmountRequest = PaymentRequest.builder()
                .amount(new BigDecimal("75000.00"))
                .currency("USD")
                .paymentMethod("CRYPTOCURRENCY")
                .build();
    }

    @Test
    void calculateFraudScore_LowAmountNewUser_ReturnsModerateScore() {
        // Given
        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findUserPaymentsBetween(eq(testUser), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        BigDecimal score = fraudDetectionService.calculateFraudScore(lowAmountRequest, testUser);

        // Then
        assertTrue(score.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(score.compareTo(new BigDecimal("0.3")) < 0); // Should be moderate risk
    }

    @Test
    void calculateFraudScore_HighAmountNewUser_ReturnsHighScore() {
        // Given
        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findUserPaymentsBetween(eq(testUser), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        BigDecimal score = fraudDetectionService.calculateFraudScore(veryHighAmountRequest, testUser);

        // Then
        assertTrue(score.compareTo(new BigDecimal("0.5")) > 0); // Should be high risk
    }

    @Test
    void calculateFraudScore_ExistingUserNormalAmount_ReturnsLowScore() {
        // Given
        List<Payment> existingPayments = Arrays.asList(
                createPayment(new BigDecimal("90.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("110.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("95.00"), PaymentStatus.COMPLETED)
        );

        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser))
                .thenReturn(existingPayments);
        when(paymentRepository.findUserPaymentsBetween(eq(testUser), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        BigDecimal score = fraudDetectionService.calculateFraudScore(lowAmountRequest, testUser);

        // Then
        assertTrue(score.compareTo(new BigDecimal("0.1")) < 0); // Should be low risk
    }

    @Test
    void calculateFraudScore_UnusualAmountPattern_ReturnsHigherScore() {
        // Given
        List<Payment> existingPayments = Arrays.asList(
                createPayment(new BigDecimal("50.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("60.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("55.00"), PaymentStatus.COMPLETED)
        );

        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser))
                .thenReturn(existingPayments);
        when(paymentRepository.findUserPaymentsBetween(eq(testUser), any(), any()))
                .thenReturn(Collections.emptyList());

        // Payment is 5x larger than average
        PaymentRequest unusualRequest = PaymentRequest.builder()
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .build();

        // When
        BigDecimal score = fraudDetectionService.calculateFraudScore(unusualRequest, testUser);

        // Then
        assertTrue(score.compareTo(new BigDecimal("0.15")) > 0); // Should be higher due to unusual pattern
    }

    @Test
    void calculateFraudScore_HighFrequency_ReturnsHighScore() {
        // Given
        List<Payment> recentPayments = Arrays.asList(
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED),
                createPayment(new BigDecimal("100.00"), PaymentStatus.COMPLETED)
        );

        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser))
                .thenReturn(recentPayments);
        when(paymentRepository.findUserPaymentsBetween(eq(testUser), any(), any()))
                .thenReturn(recentPayments); // Simulate all payments in last hour

        // When
        BigDecimal score = fraudDetectionService.calculateFraudScore(lowAmountRequest, testUser);

        // Then
        assertTrue(score.compareTo(new BigDecimal("0.1")) > 0); // Should be higher due to frequency
    }

    @Test
    void isHighRisk_HighFraudScore_ReturnsTrue() {
        // Given
        BigDecimal highScore = new BigDecimal("0.8");

        // When
        boolean result = fraudDetectionService.isHighRisk(highScore);

        // Then
        assertTrue(result);
    }

    @Test
    void isHighRisk_LowFraudScore_ReturnsFalse() {
        // Given
        BigDecimal lowScore = new BigDecimal("0.2");

        // When
        boolean result = fraudDetectionService.isHighRisk(lowScore);

        // Then
        assertFalse(result);
    }

    @Test
    void getRiskLevel_VariousScores_ReturnsCorrectLevels() {
        // Test low risk
        assertEquals("LOW", fraudDetectionService.getRiskLevel(new BigDecimal("0.1")));

        // Test medium risk
        assertEquals("MEDIUM", fraudDetectionService.getRiskLevel(new BigDecimal("0.5")));

        // Test high risk
        assertEquals("HIGH", fraudDetectionService.getRiskLevel(new BigDecimal("0.8")));
    }

    private Payment createPayment(BigDecimal amount, PaymentStatus status) {
        return Payment.builder()
                .user(testUser)
                .amount(amount)
                .currency("USD")
                .status(status)
                .paymentMethod("CREDIT_CARD")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}