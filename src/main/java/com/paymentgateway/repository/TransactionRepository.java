package com.paymentgateway.repository;

import com.paymentgateway.model.entity.Payment;
import com.paymentgateway.model.entity.Transaction;
import com.paymentgateway.model.entity.TransactionStatus;
import com.paymentgateway.model.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByPayment(Payment payment);

    List<Transaction> findByPaymentOrderByCreatedAtDesc(Payment payment);

    List<Transaction> findByTransactionType(TransactionType transactionType);

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByPaymentAndTransactionType(Payment payment, TransactionType transactionType);

    List<Transaction> findByPaymentAndStatus(Payment payment, TransactionStatus status);

    Optional<Transaction> findByGatewayTransactionId(String gatewayTransactionId);

    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findTransactionsBetween(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM Transaction t WHERE t.payment = :payment AND t.transactionType = :type AND t.status = :status")
    List<Transaction> findByPaymentAndTypeAndStatus(@Param("payment") Payment payment,
                                                    @Param("type") TransactionType type,
                                                    @Param("status") TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.processedAt IS NULL AND t.status = 'PENDING'")
    List<Transaction> findPendingUnprocessedTransactions();

    @Query("SELECT t FROM Transaction t WHERE t.processedAt BETWEEN :startDate AND :endDate")
    List<Transaction> findTransactionsProcessedBetween(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.payment = :payment AND t.transactionType = :type")
    long countByPaymentAndType(@Param("payment") Payment payment, @Param("type") TransactionType type);

    @Query("SELECT t.status, COUNT(t) FROM Transaction t GROUP BY t.status")
    List<Object[]> getTransactionStatusCounts();

    @Query("SELECT t.transactionType, COUNT(t) FROM Transaction t GROUP BY t.transactionType")
    List<Object[]> getTransactionTypeCounts();

    @Query("SELECT t FROM Transaction t WHERE t.payment.user.id = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findUserTransactions(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t WHERE t.payment.user.id = :userId AND t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findUserTransactionsBetween(@Param("userId") Long userId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
}