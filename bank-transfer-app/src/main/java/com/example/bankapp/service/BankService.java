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
    
    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Customer not found"));
    }

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

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(TransferRequest request) {
        
        // Validate accounts
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new BankException("Sender and receiver must be different accounts");
        }
        
        Customer sender = customerRepository.getByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found"));
                
        Customer receiver = customerRepository.getByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new AccountNotFoundException("Receiver account not found"));

        // Calculating 1% commission and net amount to be credited to the receiver
        BigDecimal transferAmount = request.getAmount();
        BigDecimal commission = transferAmount.multiply(new BigDecimal("0.01"));
        BigDecimal netAmountToReceiver = transferAmount.subtract(commission);

        // Debit the sender (UPDATE the sender's balance with the full amount)
        int debitCount = customerRepository.debit(sender.getAccountNumber(), transferAmount);
        if (debitCount == 0) {
            throw new InsufficientBalanceException("Insufficient balance in account " + sender.getAccountNumber());
        }

        // Retrieve or create the bank's commission account (account number: 999999)
        customerRepository.getByAccountNumber("999999").orElseGet(() -> {
            Customer bankAccount = new Customer();
            bankAccount.setAccountNumber("999999");
            bankAccount.setFullName("Bank Commission Account");
            bankAccount.setEmail("commission@bank.local"); 
            bankAccount.setBalance(BigDecimal.ZERO);
            return customerRepository.save(bankAccount);
        });

        // Simulate a crash if the checkbox is ticked
        if (request.isSimulateFailure()) {
            throw new RuntimeException("Simulated system failure after the withdrawal");
        }

        // Credit the receiver (With the NET amount after commission)
        customerRepository.credit(receiver.getAccountNumber(), netAmountToReceiver);

        // Credit the bank's commission account with the commission amount
        customerRepository.credit("999999", commission);

        // Save a SUCCESS record
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(sender.getAccountNumber());
        transaction.setToAccount(receiver.getAccountNumber());
        transaction.setAmount(transferAmount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Transfer completed. Commission charged: Rs." + commission);
        transaction.setCreatedAt(LocalDateTime.now());
        
        return transactionRepository.save(transaction);
    }

    // ==========================================
    // Deposit / Withdraw / Large Transfers
    // ==========================================

    @Transactional
    public void deposit(String accountNumber, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than 0");
        }
        
        // 1. Validate customer exists (removed assignment to fix unused variable warning)
        getCustomer(accountNumber);
        
        // 2. Credit the amount to the customer's balance
        customerRepository.credit(accountNumber, amount);
        
        // 3. Save a transaction record for the deposit
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount("CASH"); // Fix for NOT NULL constraint
        transaction.setToAccount(accountNumber);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Cash Deposit");
        transaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    @Transactional
    public void withdraw(String accountNumber, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than 0");
        }
        
        // 1. Validate customer exists (removed assignment to fix unused variable warning)
        getCustomer(accountNumber);
        
        // 2. Deduct the amount from the customer's balance
        int debitCount = customerRepository.debit(accountNumber, amount);
        if (debitCount == 0) {
            throw new InsufficientBalanceException("Insufficient balance for withdrawal");
        }
        
        // 3. Save a transaction record for the withdrawal
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(accountNumber); 
        transaction.setToAccount("CASH"); // Fix for NOT NULL constraint
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Cash Withdrawal");
        transaction.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal minAmount) {
        return transactionRepository.findAllLatest().stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(minAmount) >= 0)
                .toList();
    }
}