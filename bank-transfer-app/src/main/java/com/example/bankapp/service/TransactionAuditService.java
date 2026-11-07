package com.example.bankapp.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository transactionRepository;

    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(TransferRequest request, String errorMessage) {
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(request.getFromAccount());
        transaction.setToAccount(request.getToAccount());
        transaction.setAmount(request.getAmount());
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setDescription("FAILED: " + errorMessage);
        transaction.setCreatedAt(LocalDateTime.now());
        
        transactionRepository.save(transaction);
    }
}