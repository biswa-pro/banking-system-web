
package com.example.banking.service;

import com.example.banking.exception.BankingException;
import com.example.banking.model.*;
import com.example.banking.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class BankingService {

    private final BankAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final TransactionFileLogger logger;
    private final UserRepository users;
    private final EmailService emailService;

    public BankingService(
            BankAccountRepository accounts,
            BankTransactionRepository transactions,
            TransactionFileLogger logger,
            UserRepository users,
            EmailService emailService) {

        this.accounts = accounts;
        this.transactions = transactions;
        this.logger = logger;
        this.users = users;
        this.emailService = emailService;
    }

    // Create a bank account owned by the logged-in user.
    @Transactional
    public BankAccount create(
            String name,
            String email,
            String type,
            BigDecimal initialDeposit,
            String username) {

        if (name == null || name.isBlank()
                || email == null
                || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {

            throw new BankingException(
                    "Enter a valid name and email.",
                    HttpStatus.BAD_REQUEST);
        }

        if (type == null) {
            throw new BankingException(
                    "Account type must be SAVINGS or CURRENT.",
                    HttpStatus.BAD_REQUEST);
        }

        AccountType accountType;

        try {
            accountType = AccountType.valueOf(
                    type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BankingException(
                    "Account type must be SAVINGS or CURRENT.",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal initial = validateAmount(initialDeposit, true);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User owner = users.findByUsername(username)
                .orElseThrow(() -> new BankingException(
                        "Logged-in user not found.",
                        HttpStatus.UNAUTHORIZED));

        if (accounts.existsByOwner_Id(owner.getId())) {
            throw new BankingException(
                    "You already have a bank account. You cannot create another one.",
                    HttpStatus.CONFLICT);
        }

        if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BankingException(
                    "Email already has an account.",
                    HttpStatus.CONFLICT);
        }

        BankAccount account = accounts.save(
                new BankAccount(
                        name.trim(),
                        normalizedEmail,
                        accountType,
                        initial,
                        owner));

        record(account, null, "ACCOUNT_OPENED", initial, "Account created");

        return account;
    }

    // Return an account only if it belongs to the logged-in user.
    @Transactional(readOnly = true)
    public BankAccount getAccount(Long id, String username) {

        BankAccount account = accounts.findById(id)
                .orElseThrow(() -> new BankingException(
                        "Account not found.",
                        HttpStatus.NOT_FOUND));

        checkOwnership(account, username);

        return account;
    }

    // Deposit money into the user's own account.
    @Transactional
    public BankAccount deposit(
            Long id,
            BigDecimal amount,
            String username) {

        BigDecimal value = validateAmount(amount, false);
        BankAccount account = lockAccount(id);

        checkOwnership(account, username);

        account.setBalance(account.getBalance().add(value));

        record(account, null, "DEPOSIT", value, "Cash deposit");

        scheduleTransactionEmail(
                account.getOwner().getEmail(),
                "DEPOSIT",
                value,
                account.getBalance());

        return account;
    }

    // Withdraw money only from the user's own account.
    @Transactional
    public BankAccount withdraw(
            Long id,
            BigDecimal amount,
            String username) {

        BigDecimal value = validateAmount(amount, false);
        BankAccount account = lockAccount(id);

        checkOwnership(account, username);

        if (account.getBalance().compareTo(value) < 0) {
            throw new BankingException(
                    "Insufficient balance.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        account.setBalance(account.getBalance().subtract(value));

        record(account, null, "WITHDRAWAL", value, "Cash withdrawal");

        scheduleTransactionEmail(
                account.getOwner().getEmail(),
                "WITHDRAWAL",
                value,
                account.getBalance());

        return account;
    }

    // Transfer money from the logged-in user's account.
    @Transactional
    public void transfer(
            Long fromId,
            Long toId,
            BigDecimal amount,
            String username) {

        if (fromId == null || toId == null) {
            throw new BankingException(
                    "Both account IDs are required.",
                    HttpStatus.BAD_REQUEST);
        }

        if (fromId.equals(toId)) {
            throw new BankingException(
                    "Source and destination must differ.",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal value = validateAmount(amount, false);

        // Lock accounts in a consistent ID order to reduce deadlocks.
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        BankAccount first = lockAccount(firstId);
        BankAccount second = lockAccount(secondId);

        BankAccount source =
                first.getId().equals(fromId) ? first : second;

        BankAccount destination =
                first.getId().equals(toId) ? first : second;

        checkOwnership(source, username);

        if (source.getBalance().compareTo(value) < 0) {
            throw new BankingException(
                    "Insufficient balance.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        source.setBalance(source.getBalance().subtract(value));
        destination.setBalance(destination.getBalance().add(value));

        record(
                source,
                destination.getId(),
                "TRANSFER_OUT",
                value,
                "Transfer to account " + destination.getId());

        record(
                destination,
                source.getId(),
                "TRANSFER_IN",
                value,
                "Transfer from account " + source.getId());

        // Notify the sender after the transaction commits.
        scheduleTransactionEmail(
                source.getOwner().getEmail(),
                "TRANSFER_OUT",
                value,
                source.getBalance());

        // Notify the receiver after the transaction commits.
        if (destination.getOwner() != null) {
            scheduleTransactionEmail(
                    destination.getOwner().getEmail(),
                    "TRANSFER_IN",
                    value,
                    destination.getBalance());
        }
    }

    // Return transaction history only for the account owner.
    @Transactional(readOnly = true)
    public List<BankTransaction> history(Long id, String username) {

        getAccount(id, username);

        return transactions.findByAccountIdOrderByCreatedAtDesc(id);
    }

    // Schedule the email to be sent only after the DB transaction commits.
    private void scheduleTransactionEmail(
            String email,
            String operation,
            BigDecimal amount,
            BigDecimal balance) {

        if (email == null || email.isBlank()) {
            return;
        }

        Runnable sendEmail = () -> {
            try {
                emailService.sendTransactionAlert(
                        email,
                        operation,
                        amount.doubleValue(),
                        balance.doubleValue());
            } catch (Exception e) {
                System.err.println(
                        "Transaction alert email could not be sent: "
                                + e.getMessage());
            }
        };

        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendEmail.run();
                        }
                    });
        } else {
            sendEmail.run();
        }
    }

    // Shared ownership validation.
    private void checkOwnership(
            BankAccount account,
            String username) {

        if (account.getOwner() == null
                || username == null
                || !account.getOwner().getUsername().equals(username)) {

            throw new BankingException(
                    "You are not allowed to access this account.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private BankAccount lockAccount(Long id) {

        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new BankingException(
                        "Account not found.",
                        HttpStatus.NOT_FOUND));
    }

    private BigDecimal validateAmount(
            BigDecimal amount,
            boolean allowZero) {

        if (amount == null
                || amount.scale() > 2
                || amount.compareTo(
                        BigDecimal.ZERO) < (allowZero ? 0 : 1)) {

            throw new BankingException(
                    allowZero
                            ? "Amount must be zero or positive with at most two decimals."
                            : "Amount must be greater than zero with at most two decimals.",
                    HttpStatus.BAD_REQUEST);
        }

        return amount.setScale(2);
    }

    private void record(
            BankAccount account,
            Long relatedId,
            String type,
            BigDecimal amount,
            String description) {

        BankTransaction transaction = new BankTransaction(
                account.getId(),
                relatedId,
                type,
                amount,
                account.getBalance(),
                description);

        transactions.save(transaction);

        logger.log(
                "account=" + account.getId()
                        + ", type=" + type
                        + ", amount=" + amount
                        + ", balance=" + account.getBalance());
    }
}
