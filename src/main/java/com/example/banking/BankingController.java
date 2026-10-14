package com.example.banking;

import com.example.banking.model.BankAccount;
import com.example.banking.model.BankTransaction;
import com.example.banking.service.BankingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public record CreateRequest(
        String fullName,
        String email,
        String accountType,
        BigDecimal initialDeposit
    ) {}

    public record MoneyRequest(BigDecimal amount) {}

    public record TransferRequest(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount
    ) {}

    @PostMapping("/accounts")
    public ResponseEntity<BankAccount> create(
            @RequestBody CreateRequest request) {

        BankAccount account = service.create(
            request.fullName(),
            request.email(),
            request.accountType(),
            request.initialDeposit()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(account);
    }

    @GetMapping("/accounts/{id}")
    public BankAccount get(@PathVariable Long id) {
        return service.getAccount(id);
    }

    @PostMapping("/accounts/{id}/deposit")
    public BankAccount deposit(
            @PathVariable Long id,
            @RequestBody MoneyRequest request) {

        return service.deposit(id, request.amount());
    }

    @PostMapping("/accounts/{id}/withdraw")
    public BankAccount withdraw(
            @PathVariable Long id,
            @RequestBody MoneyRequest request) {

        return service.withdraw(id, request.amount());
    }

    @PostMapping("/transfers")
    public ResponseEntity<Void> transfer(
            @RequestBody TransferRequest request) {

        service.transfer(
            request.fromAccountId(),
            request.toAccountId(),
            request.amount()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts/{id}/transactions")
    public List<BankTransaction> history(
            @PathVariable Long id) {

        return service.history(id);
    }
}