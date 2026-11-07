package com.example.bankapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    // View Methods for the Dashboard
    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAllOrdered();
        }
        return customerRepository.searchByName(keyword);
    }

    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        return transactionRepository.findHistory(accountNumber);
    }

    // Write Methods (Needs read-write Transactions)
    
    @Transactional
    public void createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number already exists.");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email already exists.");
        }
        customerRepository.save(customer);
    }

    // The heart of this lab - Transfer logic with Rollback
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(TransferRequest request) {
        
        // Step 1: Validate accounts
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new BankException("Sender and receiver must be different accounts");
        }
        
        Customer sender = customerRepository.getByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found"));
                
        Customer receiver = customerRepository.getByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new AccountNotFoundException("Receiver account not found"));

        // Step 2: Debit the sender (UPDATE the sender's balance)
        int debitCount = customerRepository.debit(sender.getAccountNumber(), request.getAmount());
        if (debitCount == 0) {
            throw new InsufficientBalanceException("Insufficient balance in account " + sender.getAccountNumber());
        }

        // Step 3: Simulate a crash if the checkbox is ticked
        if (request.isSimulateFailure()) {
            throw new RuntimeException("Simulated system failure after the withdrawal");
        }

        // Step 4: Credit the receiver
        customerRepository.credit(receiver.getAccountNumber(), request.getAmount());

        // Step 5: Save a SUCCESS record
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(sender.getAccountNumber());
        transaction.setToAccount(receiver.getAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Transfer completed");
        transaction.setCreatedAt(LocalDateTime.now());
        
        return transactionRepository.save(transaction);
    }
}