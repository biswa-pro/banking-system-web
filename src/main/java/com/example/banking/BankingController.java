
package com.example.banking;

import com.example.banking.model.BankAccount;
import com.example.banking.model.BankTransaction;
import com.example.banking.service.BankingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BankingController {

    private final BankingService service;

    public BankingController(BankingService service) {
        this.service = service;
    }

    // Request body for creating an account
    public record CreateRequest(
        String fullName,
        String email,
        String accountType,
        BigDecimal initialDeposit
    ) {}

    // Request body for deposit and withdrawal
    public record MoneyRequest(BigDecimal amount) {}

    // Request body for transferring money
    public record TransferRequest(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount
    ) {}

    // Create an account for the logged-in user
    @PostMapping("/accounts")
    public ResponseEntity<BankAccount> create(
            @RequestBody CreateRequest request,
            Authentication authentication) {

        BankAccount account = service.create(
            request.fullName(),
            request.email(),
            request.accountType(),
            request.initialDeposit(),
            authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(account);
    }

    // Get an account only if it belongs to the logged-in user
    @GetMapping("/accounts/{id}")
    public BankAccount get(
            @PathVariable Long id,
            Authentication authentication) {

        return service.getAccount(
            id,
            authentication.getName()
        );
    }

    // Deposit money into the user's own account
    @PostMapping("/accounts/{id}/deposit")
    public BankAccount deposit(
            @PathVariable Long id,
            @RequestBody MoneyRequest request,
            Authentication authentication) {

        return service.deposit(
            id,
            request.amount(),
            authentication.getName()
        );
    }

    // Withdraw money from the user's own account
    @PostMapping("/accounts/{id}/withdraw")
    public BankAccount withdraw(
            @PathVariable Long id,
            @RequestBody MoneyRequest request,
            Authentication authentication) {

        return service.withdraw(
            id,
            request.amount(),
            authentication.getName()
        );
    }

    // Transfer money from the user's own account
    @PostMapping("/transfers")
    public ResponseEntity<Void> transfer(
            @RequestBody TransferRequest request,
            Authentication authentication) {

        service.transfer(
            request.fromAccountId(),
            request.toAccountId(),
            request.amount(),
            authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    // Get transaction history only for the user's own account
    @GetMapping("/accounts/{id}/transactions")
    public List<BankTransaction> history(
            @PathVariable Long id,
            Authentication authentication) {

        return service.history(
            id,
            authentication.getName()
        );
    }
}
