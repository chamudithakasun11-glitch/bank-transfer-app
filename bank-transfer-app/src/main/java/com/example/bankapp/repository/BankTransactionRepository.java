package com.example.bankapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. All transactions, newest first
    @Query("SELECT b FROM BankTransaction b ORDER BY b.createdAt DESC")
    List<BankTransaction> findAllLatest();

    // 2. Transactions where the account is the sender or the receiver (OR condition)
    @Query("SELECT b FROM BankTransaction b WHERE b.fromAccount = :accountNumber OR b.toAccount = :accountNumber ORDER BY b.createdAt DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    // 3. Filters by status; an enum can be passed as a parameter
    @Query("SELECT b FROM BankTransaction b WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. Number of SUCCESS or FAILED transactions for the dashboard
    @Query("SELECT COUNT(b) FROM BankTransaction b WHERE b.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);
}