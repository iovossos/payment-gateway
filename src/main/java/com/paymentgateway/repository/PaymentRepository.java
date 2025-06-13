package com.paymentgateway.repository;

import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.PaymentStatus;
import com.paymentgateway.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUser(User user);

    List<Payment> findByUserOrderByCreatedAtDesc(User user);

    Page<Payment> findByUser(User user, Pageable pageable);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByUserAndStatus(User user, PaymentStatus status);

    Optional<Payment> findByMerchantReference(String merchantReference);

    List<Payment> findByPaymentMethod(String paymentMethod);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsBetween(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.user = :user AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findUserPaymentsBetween(@Param("user") User user,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.amount >= :minAmount AND p.amount <= :maxAmount")
    List<Payment> findPaymentsByAmountRange(@Param("minAmount") BigDecimal minAmount,
                                            @Param("maxAmount") BigDecimal maxAmount);

    @Query("SELECT p FROM Payment p WHERE p.fraudScore >= :threshold")
    List<Payment> findHighRiskPayments(@Param("threshold") BigDecimal threshold);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.user = :user AND p.status = :status")
    BigDecimal getTotalAmountByUserAndStatus(@Param("user") User user,
                                             @Param("status") PaymentStatus status);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'COMPLETED' AND p.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSuccessfulPaymentsBetween(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.user = :user AND p.status = :status")
    long countUserPaymentsByStatus(@Param("user") User user, @Param("status") PaymentStatus status);

    @Query("SELECT p.status, COUNT(p) FROM Payment p GROUP BY p.status")
    List<Object[]> getPaymentStatusCounts();

    @Query("SELECT p.paymentMethod, COUNT(p) FROM Payment p GROUP BY p.paymentMethod")
    List<Object[]> getPaymentMethodCounts();

    @Query("SELECT p FROM Payment p WHERE p.user = :user AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Payment> findUserPaymentsByStatuses(@Param("user") User user,
                                             @Param("statuses") List<PaymentStatus> statuses);
}