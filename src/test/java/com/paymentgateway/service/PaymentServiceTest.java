package com.paymentgateway.service;

import com.paymentgateway.exception.FraudDetectedException;
import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.dto.PaymentResponse;
import com.paymentgateway.model.entity.*;
import com.paymentgateway.repository.PaymentRepository;
import com.paymentgateway.repository.TransactionRepository;
import com.paymentgateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    private User testUser;
    private PaymentRequest validPaymentRequest;
    private PaymentRequest.CardDetails validCardDetails;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
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

        testPayment = Payment.builder()
                .id(1L)
                .user(testUser)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentMethod("CREDIT_CARD")
                .merchantReference("TEST-REF-001")
                .description("Test payment")
                .fraudScore(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void processPayment_ValidRequest_ReturnsSuccessfulPayment() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(fraudDetectionService.calculateFraudScore(any(PaymentRequest.class), any(User.class)))
                .thenReturn(new BigDecimal("0.1"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(createMockTransaction());

        // When
        PaymentResponse response = paymentService.processPayment(validPaymentRequest, "testuser");

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertNotNull(response.getGatewayTransactionId());
        assertEquals("Payment processed successfully", response.getMessage());

        verify(userRepository).findByUsername("testuser");
        verify(fraudDetectionService).calculateFraudScore(validPaymentRequest, testUser);
        verify(paymentRepository, times(2)).save(any(Payment.class)); // Called twice: create + update status
        verify(transactionRepository).save(any(Transaction.class));
        verify(notificationService).sendPaymentConfirmation(any(Payment.class));
    }

    @Test
    void processPayment_HighFraudScore_ThrowsFraudDetectedException() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(fraudDetectionService.calculateFraudScore(any(PaymentRequest.class), any(User.class)))
                .thenReturn(new BigDecimal("0.8")); // High fraud score

        // When & Then
        FraudDetectedException exception = assertThrows(FraudDetectedException.class,
                () -> paymentService.processPayment(validPaymentRequest, "testuser"));

        assertEquals("Payment blocked due to high fraud risk", exception.getMessage());
        verify(fraudDetectionService).calculateFraudScore(validPaymentRequest, testUser);
        verify(paymentRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void processPayment_UserNotFound_ThrowsPaymentException() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validPaymentRequest, "nonexistent"));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findByUsername("nonexistent");
        verify(fraudDetectionService, never()).calculateFraudScore(any(), any());
    }

    @Test
    void processPayment_NullRequest_ThrowsPaymentException() {
        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(null, "testuser"));

        assertEquals("Payment request cannot be null", exception.getMessage());
    }

    @Test
    void processPayment_NullUsername_ThrowsPaymentException() {
        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validPaymentRequest, null));

        assertEquals("Username cannot be null or empty", exception.getMessage());
    }

    @Test
    void processPayment_DuplicateMerchantReference_ThrowsPaymentException() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(paymentRepository.findByMerchantReference("TEST-REF-001"))
                .thenReturn(Optional.of(testPayment));

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.processPayment(validPaymentRequest, "testuser"));

        assertEquals("Merchant reference already exists", exception.getMessage());
        verify(paymentRepository).findByMerchantReference("TEST-REF-001");
    }

    @Test
    void getPaymentById_ExistingPayment_ReturnsPayment() {
        // Given
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        // When
        Optional<Payment> result = paymentService.getPaymentById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testPayment.getId(), result.get().getId());
        verify(paymentRepository).findById(1L);
    }

    @Test
    void getPaymentById_NonExistingPayment_ReturnsEmpty() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Payment> result = paymentService.getPaymentById(999L);

        // Then
        assertFalse(result.isPresent());
        verify(paymentRepository).findById(999L);
    }

    @Test
    void getUserPayments_ExistingUser_ReturnsPayments() {
        // Given
        List<Payment> payments = Arrays.asList(testPayment);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(paymentRepository.findByUserOrderByCreatedAtDesc(testUser)).thenReturn(payments);

        // When
        List<Payment> result = paymentService.getUserPayments("testuser");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPayment.getId(), result.get(0).getId());
        verify(paymentRepository).findByUserOrderByCreatedAtDesc(testUser);
    }

    @Test
    void getUserPayments_NonExistingUser_ThrowsPaymentException() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.getUserPayments("nonexistent"));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void refundPayment_ValidPayment_ReturnsSuccessfulRefund() {
        // Given
        testPayment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(createMockRefundTransaction());

        // When
        PaymentResponse response = paymentService.refundPayment(1L, new BigDecimal("100.00"), "Full refund");

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals("Refund processed successfully", response.getMessage());

        verify(paymentRepository).findById(1L);
        verify(paymentRepository).save(any(Payment.class)); // Only called once for refund (status update)
        verify(transactionRepository).save(any(Transaction.class));
        verify(notificationService).sendRefundConfirmation(any(Payment.class), any(BigDecimal.class));
    }

    @Test
    void refundPayment_PaymentNotFound_ThrowsPaymentException() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.refundPayment(999L, new BigDecimal("100.00"), "Refund"));

        assertEquals("Payment not found", exception.getMessage());
    }

    @Test
    void refundPayment_PaymentNotCompleted_ThrowsPaymentException() {
        // Given
        testPayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.refundPayment(1L, new BigDecimal("100.00"), "Refund"));

        assertEquals("Payment cannot be refunded in current status", exception.getMessage());
    }

    @Test
    void refundPayment_InvalidAmount_ThrowsPaymentException() {
        // Given
        testPayment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> paymentService.refundPayment(1L, new BigDecimal("200.00"), "Invalid refund"));

        assertEquals("Refund amount cannot exceed payment amount", exception.getMessage());
    }

    @Test
    void getPaymentsByStatus_ValidStatus_ReturnsPayments() {
        // Given
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentRepository.findByStatus(PaymentStatus.COMPLETED)).thenReturn(payments);

        // When
        List<Payment> result = paymentService.getPaymentsByStatus(PaymentStatus.COMPLETED);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(paymentRepository).findByStatus(PaymentStatus.COMPLETED);
    }

    @Test
    void getTotalAmountByUser_ValidUser_ReturnsAmount() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(paymentRepository.getTotalAmountByUserAndStatus(testUser, PaymentStatus.COMPLETED))
                .thenReturn(new BigDecimal("500.00"));

        // When
        BigDecimal result = paymentService.getTotalAmountByUser("testuser", PaymentStatus.COMPLETED);

        // Then
        assertEquals(new BigDecimal("500.00"), result);
        verify(paymentRepository).getTotalAmountByUserAndStatus(testUser, PaymentStatus.COMPLETED);
    }

    private Transaction createMockTransaction() {
        return Transaction.builder()
                .id(1L)
                .payment(testPayment)
                .transactionType(TransactionType.PAYMENT)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.SUCCESS)
                .gatewayTransactionId("TXN-12345")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Transaction createMockRefundTransaction() {
        return Transaction.builder()
                .id(2L)
                .payment(testPayment)
                .transactionType(TransactionType.REFUND)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.SUCCESS)
                .gatewayTransactionId("RFD-12345")
                .createdAt(LocalDateTime.now())
                .build();
    }
}