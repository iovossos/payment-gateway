package com.paymentgateway.service;

import com.paymentgateway.model.dto.PaymentRequest;
import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.PaymentStatus;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final PaymentRepository paymentRepository;

    // LOWERED THRESHOLDS FOR BETTER FRAUD DETECTION
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("5000.00");  // Lowered from 10k
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = new BigDecimal("15000.00");  // Lowered from 50k
    private static final int MAX_PAYMENTS_PER_HOUR = 3;  // Lowered from 10
    private static final int MAX_PAYMENTS_PER_DAY = 10;  // Lowered from 50

    public BigDecimal calculateFraudScore(PaymentRequest request, User user) {
        log.info("Calculating fraud score for user: {}, amount: {}, method: {}",
                user.getUsername(), request.getAmount(), request.getPaymentMethod());

        BigDecimal score = BigDecimal.ZERO;

        // Factor 1: Amount-based risk (0.0 - 0.5) - INCREASED MAX
        BigDecimal amountRisk = calculateAmountRisk(request.getAmount());
        score = score.add(amountRisk);
        log.debug("Amount risk: {}", amountRisk);

        // Factor 2: User behavior pattern (0.0 - 0.3)
        BigDecimal behaviorRisk = calculateUserBehaviorRisk(user, request);
        score = score.add(behaviorRisk);
        log.debug("Behavior risk: {}", behaviorRisk);

        // Factor 3: Payment frequency (0.0 - 0.4) - INCREASED MAX
        BigDecimal frequencyRisk = calculateFrequencyRisk(user);
        score = score.add(frequencyRisk);
        log.debug("Frequency risk: {}", frequencyRisk);

        // Factor 4: Payment method risk (0.0 - 0.2) - INCREASED MAX
        BigDecimal methodRisk = calculatePaymentMethodRisk(request.getPaymentMethod());
        score = score.add(methodRisk);
        log.debug("Method risk: {}", methodRisk);

        // Ensure score doesn't exceed 1.0
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }

        BigDecimal finalScore = score.setScale(2, RoundingMode.HALF_UP);
        log.warn("FRAUD SCORE CALCULATED: {} for user: {}, amount: {}, method: {}",
                finalScore, user.getUsername(), request.getAmount(), request.getPaymentMethod());

        return finalScore;
    }

    private BigDecimal calculateAmountRisk(BigDecimal amount) {
        if (amount.compareTo(VERY_HIGH_AMOUNT_THRESHOLD) >= 0) {
            return new BigDecimal("0.5");  // Increased from 0.4
        } else if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            return new BigDecimal("0.3");  // Increased from 0.2
        } else if (amount.compareTo(new BigDecimal("1000.00")) >= 0) {
            return new BigDecimal("0.1");
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateUserBehaviorRisk(User user, PaymentRequest request) {
        BigDecimal risk = BigDecimal.ZERO;

        // Check user's payment history
        List<Payment> userPayments = paymentRepository.findByUserOrderByCreatedAtDesc(user);

        if (userPayments.isEmpty()) {
            // New user - higher risk
            risk = risk.add(new BigDecimal("0.2"));  // Increased from 0.15
            log.debug("New user detected - adding 0.2 risk");
        } else {
            // Check for unusual amount patterns
            BigDecimal avgAmount = calculateAveragePaymentAmount(userPayments);
            if (avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = request.getAmount().divide(avgAmount, 2, RoundingMode.HALF_UP);
                if (ratio.compareTo(new BigDecimal("10.0")) > 0) {
                    // Payment is 10x larger than average - VERY SUSPICIOUS
                    risk = risk.add(new BigDecimal("0.3"));
                    log.debug("Payment 10x larger than average - adding 0.3 risk");
                } else if (ratio.compareTo(new BigDecimal("5.0")) > 0) {
                    // Payment is 5x larger than average
                    risk = risk.add(new BigDecimal("0.2"));
                    log.debug("Payment 5x larger than average - adding 0.2 risk");
                } else if (ratio.compareTo(new BigDecimal("3.0")) > 0) {
                    // Payment is 3x larger than average
                    risk = risk.add(new BigDecimal("0.1"));
                    log.debug("Payment 3x larger than average - adding 0.1 risk");
                }
            }

            // Check for failed payment attempts
            long failedPayments = userPayments.stream()
                    .filter(p -> PaymentStatus.FAILED.equals(p.getStatus()))
                    .count();

            if (failedPayments > 3) {
                risk = risk.add(new BigDecimal("0.1"));
                log.debug("Multiple failed payments detected - adding 0.1 risk");
            }
        }

        return risk;
    }

    private BigDecimal calculateFrequencyRisk(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime oneDayAgo = now.minusDays(1);

        // Check payments in last hour - MORE AGGRESSIVE
        List<Payment> paymentsLastHour = paymentRepository.findUserPaymentsBetween(user, oneHourAgo, now);
        log.debug("Payments in last hour: {}", paymentsLastHour.size());

        if (paymentsLastHour.size() >= MAX_PAYMENTS_PER_HOUR) {
            log.warn("FREQUENCY FRAUD DETECTED: {} payments in last hour (max: {})",
                    paymentsLastHour.size(), MAX_PAYMENTS_PER_HOUR);
            return new BigDecimal("0.4");  // Increased from 0.2
        }

        // Check payments in last day
        List<Payment> paymentsLastDay = paymentRepository.findUserPaymentsBetween(user, oneDayAgo, now);
        log.debug("Payments in last day: {}", paymentsLastDay.size());

        if (paymentsLastDay.size() >= MAX_PAYMENTS_PER_DAY) {
            log.warn("DAILY FREQUENCY FRAUD DETECTED: {} payments in last day (max: {})",
                    paymentsLastDay.size(), MAX_PAYMENTS_PER_DAY);
            return new BigDecimal("0.3");  // Increased from 0.15
        }

        // Moderate frequency risk - LOWERED THRESHOLDS
        if (paymentsLastHour.size() >= 2) {  // Lowered from 5
            log.debug("Moderate hourly frequency risk: {} payments", paymentsLastHour.size());
            return new BigDecimal("0.2");  // Increased from 0.1
        }
        if (paymentsLastDay.size() >= 5) {  // Lowered from 20
            log.debug("Moderate daily frequency risk: {} payments", paymentsLastDay.size());
            return new BigDecimal("0.1");  // Increased from 0.05
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculatePaymentMethodRisk(String paymentMethod) {
        BigDecimal risk = switch (paymentMethod.toUpperCase()) {
            case "CREDIT_CARD" -> new BigDecimal("0.05");  // Increased from 0.02
            case "DEBIT_CARD" -> new BigDecimal("0.02");   // Increased from 0.01
            case "BANK_TRANSFER" -> BigDecimal.ZERO;
            case "DIGITAL_WALLET" -> new BigDecimal("0.08");  // Increased from 0.03
            case "CRYPTOCURRENCY" -> new BigDecimal("0.2");   // DOUBLED from 0.1
            default -> new BigDecimal("0.15"); // Increased from 0.05 for unknown methods
        };

        log.debug("Payment method {} assigned risk: {}", paymentMethod, risk);
        return risk;
    }

    private BigDecimal calculateAveragePaymentAmount(List<Payment> payments) {
        if (payments.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = payments.stream()
                .filter(p -> PaymentStatus.COMPLETED.equals(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long completedPayments = payments.stream()
                .filter(p -> PaymentStatus.COMPLETED.equals(p.getStatus()))
                .count();

        if (completedPayments == 0) {
            return BigDecimal.ZERO;
        }

        return total.divide(BigDecimal.valueOf(completedPayments), 2, RoundingMode.HALF_UP);
    }

    public boolean isHighRisk(BigDecimal fraudScore) {
        return fraudScore.compareTo(new BigDecimal("0.5")) > 0;  // LOWERED from 0.7
    }

    public boolean isMediumRisk(BigDecimal fraudScore) {
        return fraudScore.compareTo(new BigDecimal("0.2")) > 0 &&  // LOWERED from 0.3
                fraudScore.compareTo(new BigDecimal("0.5")) <= 0;   // LOWERED from 0.7
    }

    public boolean isLowRisk(BigDecimal fraudScore) {
        return fraudScore.compareTo(new BigDecimal("0.2")) <= 0;  // LOWERED from 0.3
    }

    public String getRiskLevel(BigDecimal fraudScore) {
        if (isHighRisk(fraudScore)) {
            return "HIGH";
        } else if (isMediumRisk(fraudScore)) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}