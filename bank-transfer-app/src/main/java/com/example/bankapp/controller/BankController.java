package com.example.bankapp.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class BankController {

    // Injecting services automatically via Lombok's @RequiredArgsConstructor
    private final BankService bankService;
    private final TransactionAuditService auditService;

    /**
     * Handles GET requests to the root URL (/) to display the dashboard.
     * Includes optional keyword search for customers.
     */
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {
        
        // Adding data to the model to display on the Thymeleaf template (bank.html)
        model.addAttribute("customers", bankService.searchCustomers(keyword));
        model.addAttribute("latestTransactions", bankService.getLatestTransactions());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        
        // Empty objects to bind with the HTML forms
        model.addAttribute("transferRequest", new TransferRequest());
        model.addAttribute("newCustomer", new Customer());
        model.addAttribute("keyword", keyword);
        
        return "bank"; // Renders bank.html template
    }

    /**
     * Handles POST requests to add a new customer.
     */
    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("newCustomer") Customer customer, 
                              BindingResult bindingResult, 
                              RedirectAttributes redirectAttributes) {
                    
        // Check if bean validation failed (e.g., empty names or invalid amounts)
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Validation failed. Please check your inputs.");
            return "redirect:/";
        }

        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success", "Customer added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/";
    }

    /**
     * Handles POST requests for transferring money.
     * The try-catch block is crucial here to handle rollbacks and record failures.
     */
    @PostMapping("/transfer")
    public String transferMoney(@Valid @ModelAttribute("transferRequest") TransferRequest request, 
                                BindingResult bindingResult, 
                                RedirectAttributes redirectAttributes) {
                    
        // Check for form validation errors (e.g., amount is empty or 0)
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Invalid transfer details provided.");
            return "redirect:/";
        }

        try {
            // This method will run inside a @Transactional and roll back if an exception occurs
            bankService.transfer(request);
            redirectAttributes.addFlashAttribute("success", "Transfer completed successfully.");
            
        } catch (Exception e) {
            // The rollback has occurred. Now, record the failure in a NEW independent transaction.
            auditService.recordFailure(request, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/";
    }

    /**
     * Handles GET requests to view the transaction history of a specific account.
     * Calculates total sent amount and fetches customer details for the view.
     */
    @GetMapping("/accounts/{accountNumber}/history")
    public String viewAccountHistory(@PathVariable("accountNumber") String accountNumber, Model model) {
        
        // 1. Fetch customer details using the account number
        Customer customer = bankService.getCustomer(accountNumber);
        
        // 2. Fetch all transactions for this account (both sent and received)
        List<BankTransaction> transactions = bankService.getAccountHistory(accountNumber);
        
        // 3. Calculate total successfully sent amount (including the 1% fee)
        BigDecimal totalSent = BigDecimal.ZERO;
        for (BankTransaction t : transactions) {
            // Check if the current account is the sender and the transaction was successful
            if (accountNumber.equals(t.getFromAccount()) && t.getStatus() == TransactionStatus.SUCCESS) {
                totalSent = totalSent.add(t.getAmount());
            }
        }
        
        // 4. Add attributes to the model for Thymeleaf rendering in history.html
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactions); // මෙතන transactions කියලා හැදුවා
        model.addAttribute("totalSent", totalSent);
        
        return "history";
    }

    // ==========================================
    // Deposit / Withdraw / Large Transfers Endpoints
    // ==========================================

    @GetMapping("/deposit")
    public String showDepositForm(Model model) {
        model.addAttribute("customers", bankService.searchCustomers(null));
        return "deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam String accountNumber, @RequestParam BigDecimal amount, RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "LKR " + amount + " deposited successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String showWithdrawForm(Model model) {
        model.addAttribute("customers", bankService.searchCustomers(null));
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdraw(@RequestParam String accountNumber, @RequestParam BigDecimal amount, RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "LKR " + amount + " withdrawn successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/withdraw";
    }

    @GetMapping("/large-transfers")
    public String showLargeTransfers(@RequestParam(name="min", defaultValue = "10000") BigDecimal min, Model model) {
    
        model.addAttribute("transfers", bankService.getLargeTransfers(min));
        model.addAttribute("minAmount", min);
        return "large-transfers";
    }
}