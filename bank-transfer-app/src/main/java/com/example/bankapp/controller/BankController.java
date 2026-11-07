package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
     */
    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable String accountNumber, Model model) {
        
        // Fetch the transaction history for the specific account
        model.addAttribute("transactions", bankService.getAccountHistory(accountNumber));
        model.addAttribute("accountNumber", accountNumber);
        
        return "history"; // Renders history.html template
    }
}