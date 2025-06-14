package com.paymentgateway.service;

import com.paymentgateway.exception.FraudDetectedException;
import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.dto.PaymentResponse;
import com.paymentgateway.model.entity.*;
import com.paymentgateway.repository.PaymentRepository;
import com.paymentgateway.repository.TransactionRepository;
import com.paymentgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;

    // LOWERED FRAUD THRESHOLD
    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("0.5");  // Lowered from 0.7

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String username) {
        // Input validation
        if (request == null) {
            throw new PaymentException("Payment request cannot be null");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new PaymentException("Username cannot be null or empty");
        }

        log.info("Processing payment for user: {}, amount: {}, method: {}",
                username, request.getAmount(), request.getPaymentMethod());

        // Find user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found"));

        // Check for duplicate merchant reference
        if (request.getMerchantReference() != null &&
                paymentRepository.findByMerchantReference(request.getMerchantReference()).isPresent()) {
            throw new PaymentException("Merchant reference already exists");
        }

        // Fraud detection
        BigDecimal fraudScore = fraudDetectionService.calculateFraudScore(request, user);
        log.warn("FRAUD SCORE: {} for payment amount: {} by user: {}", fraudScore, request.getAmount(), username);

        if (fraudScore.compareTo(FRAUD_THRESHOLD) > 0) {
            log.error("PAYMENT BLOCKED - FRAUD DETECTED! Score: {} (threshold: {}) for user: {}, amount: {}",
                    fraudScore, FRAUD_THRESHOLD, username, request.getAmount());

            // Send fraud alert
            notificationService.sendFraudAlert(
                    Payment.builder()
                            .user(user)
                            .amount(request.getAmount())
                            .paymentMethod(request.getPaymentMethod())
                            .fraudScore(fraudScore)
                            .build(),
                    fraudScore
            );

            throw new FraudDetectedException(
                    String.format("Payment blocked due to high fraud risk (score: %.2f)", fraudScore),
                    fraudScore.toString(),
                    fraudScore.doubleValue()
            );
        }

        try {
            // Create payment entity
            Payment payment = Payment.builder()
                    .user(user)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(PaymentStatus.PROCESSING)
                    .paymentMethod(request.getPaymentMethod())
                    .merchantReference(request.getMerchantReference())
                    .description(request.getDescription())
                    .fraudScore(fraudScore)
                    .build();

            Payment savedPayment = paymentRepository.save(payment);
            log.info("Payment created with ID: {} and fraud score: {}", savedPayment.getId(), fraudScore);

            // Process payment with gateway (simulated)
            String gatewayTransactionId = processWithGateway(request);

            // Create transaction record
            Transaction transaction = Transaction.builder()
                    .payment(savedPayment)
                    .transactionType(TransactionType.PAYMENT)
                    .amount(request.getAmount())
                    .status(TransactionStatus.SUCCESS)
                    .gatewayTransactionId(gatewayTransactionId)
                    .processedAt(LocalDateTime.now())
                    .build();

            transactionRepository.save(transaction);

            // Update payment status
            savedPayment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(savedPayment);

            // Send notification
            notificationService.sendPaymentConfirmation(savedPayment);

            log.info("Payment processed successfully: {} with fraud score: {}",
                    savedPayment.getId(), fraudScore);

            return PaymentResponse.success(
                    savedPayment.getId(),
                    savedPayment.getAmount(),
                    savedPayment.getCurrency(),
                    gatewayTransactionId
            );

        } catch (Exception e) {
            log.error("Payment processing failed for user: {}", username, e);
            throw new PaymentException("Payment processing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentById(Long paymentId) {
        if (paymentId == null) {
            return Optional.empty();
        }
        return paymentRepository.findById(paymentId);
    }

    @Transactional(readOnly = true)
    public List<Payment> getUserPayments(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found"));

        return paymentRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Page<Payment> getUserPayments(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found"));

        return paymentRepository.findByUser(user, pageable);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal refundAmount, String reason) {
        if (paymentId == null) {
            throw new PaymentException("Payment ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("Refund amount must be greater than zero");
        }

        log.info("Processing refund for payment: {}, amount: {}", paymentId, refundAmount);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        // Validate payment status
        if (!PaymentStatus.COMPLETED.equals(payment.getStatus())) {
            throw new PaymentException("Payment cannot be refunded in current status");
        }

        // Validate refund amount
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new PaymentException("Refund amount cannot exceed payment amount");
        }

        try {
            // Process refund with gateway (simulated)
            String gatewayTransactionId = processRefundWithGateway(payment, refundAmount);

            // Create refund transaction
            Transaction refundTransaction = Transaction.builder()
                    .payment(payment)
                    .transactionType(TransactionType.REFUND)
                    .amount(refundAmount)
                    .status(TransactionStatus.SUCCESS)
                    .gatewayTransactionId(gatewayTransactionId)
                    .gatewayResponse(reason)
                    .processedAt(LocalDateTime.now())
                    .build();

            transactionRepository.save(refundTransaction);

            // Update payment status
            if (refundAmount.compareTo(payment.getAmount()) == 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
            paymentRepository.save(payment);

            // Send notification
            notificationService.sendRefundConfirmation(payment, refundAmount);

            log.info("Refund processed successfully for payment: {}", paymentId);

            return PaymentResponse.builder()
                    .paymentId(payment.getId())
                    .amount(refundAmount)
                    .currency(payment.getCurrency())
                    .status(payment.getStatus())
                    .gatewayTransactionId(gatewayTransactionId)
                    .message("Refund processed successfully")
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Refund processing failed for payment: {}", paymentId, e);
            throw new PaymentException("Refund processing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        if (status == null) {
            throw new PaymentException("Payment status cannot be null");
        }
        return paymentRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new PaymentException("Start date and end date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new PaymentException("Start date cannot be after end date");
        }
        return paymentRepository.findPaymentsBetween(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountByUser(String username, PaymentStatus status) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found"));

        BigDecimal total = paymentRepository.getTotalAmountByUserAndStatus(user, status);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalSuccessfulPaymentsBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new PaymentException("Start date and end date cannot be null");
        }
        BigDecimal total = paymentRepository.getTotalSuccessfulPaymentsBetween(startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<Payment> getHighRiskPayments(BigDecimal fraudThreshold) {
        if (fraudThreshold == null) {
            fraudThreshold = FRAUD_THRESHOLD;
        }
        return paymentRepository.findHighRiskPayments(fraudThreshold);
    }

    @Transactional
    public void cancelPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        if (!PaymentStatus.PENDING.equals(payment.getStatus()) &&
                !PaymentStatus.PROCESSING.equals(payment.getStatus())) {
            throw new PaymentException("Payment cannot be cancelled in current status");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        // Create cancellation transaction
        Transaction cancellationTransaction = Transaction.builder()
                .payment(payment)
                .transactionType(TransactionType.ADJUSTMENT)
                .amount(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .gatewayResponse(reason)
                .processedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(cancellationTransaction);

        log.info("Payment cancelled: {}, reason: {}", paymentId, reason);
    }

    private String processWithGateway(PaymentRequest request) {
        // Simulate gateway processing
        // in real implementation this would call external payment gateway API
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String processRefundWithGateway(Payment payment, BigDecimal refundAmount) {
        // Simulate gateway processing
        // in real implementation this would call external payment gateway API
        return "RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}