package com.example.banking.service;

import com.example.banking.exception.BankingException;
import com.example.banking.model.*;
import com.example.banking.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class BankingService {

	private final BankAccountRepository accounts;
	private final BankTransactionRepository transactions;
	private final TransactionFileLogger logger;

	public BankingService(BankAccountRepository accounts, BankTransactionRepository transactions,
			TransactionFileLogger logger) {
		this.accounts = accounts;
		this.transactions = transactions;
		this.logger = logger;
	}

	@Transactional
	public BankAccount create(String name, String email, String type, BigDecimal initialDeposit) {

		if (name == null || name.isBlank() || email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new BankingException("Enter a valid name and email.", HttpStatus.BAD_REQUEST);
		}

		AccountType accountType;
		try {
			accountType = AccountType.valueOf(type.trim().toUpperCase(Locale.ROOT));
		} catch (Exception ex) {
			throw new BankingException("Account type must be SAVINGS or CURRENT.", HttpStatus.BAD_REQUEST);
		}

		BigDecimal initial = validateAmount(initialDeposit, true);
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

		if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new BankingException("Email already has an account.", HttpStatus.CONFLICT);
		}

		BankAccount account = accounts.save(new BankAccount(name.trim(), normalizedEmail, accountType, initial));

		record(account, null, "ACCOUNT_OPENED", initial, "Account created");

		return account;
	}

	@Transactional(readOnly = true)
	public BankAccount getAccount(Long id) {
		return accounts.findById(id)
				.orElseThrow(() -> new BankingException("Account not found.", HttpStatus.NOT_FOUND));
	}

	@Transactional
	public BankAccount deposit(Long id, BigDecimal amount) {
		BigDecimal value = validateAmount(amount, false);
		BankAccount account = lockAccount(id);

		account.setBalance(account.getBalance().add(value));

		record(account, null, "DEPOSIT", value, "Cash deposit");

		return account;
	}

	@Transactional
	public BankAccount withdraw(Long id, BigDecimal amount) {
		BigDecimal value = validateAmount(amount, false);
		BankAccount account = lockAccount(id);

		if (account.getBalance().compareTo(value) < 0) {
			throw new BankingException("Insufficient balance.", HttpStatus.UNPROCESSABLE_ENTITY);
		}

		account.setBalance(account.getBalance().subtract(value));

		record(account, null, "WITHDRAWAL", value, "Cash withdrawal");

		return account;
	}

	@Transactional
	public void transfer(Long fromId, Long toId, BigDecimal amount) {

		if (fromId == null || toId == null) {
			throw new BankingException("Both account IDs are required.", HttpStatus.BAD_REQUEST);
		}

		if (fromId.equals(toId)) {
			throw new BankingException("Source and destination must differ.", HttpStatus.BAD_REQUEST);
		}

		BigDecimal value = validateAmount(amount, false);

		// Lock in a consistent ID order to reduce deadlocks.
		Long firstId = Math.min(fromId, toId);
		Long secondId = Math.max(fromId, toId);

		BankAccount first = lockAccount(firstId);
		BankAccount second = lockAccount(secondId);

		BankAccount source = first.getId().equals(fromId) ? first : second;
		BankAccount destination = first.getId().equals(toId) ? first : second;

		if (source.getBalance().compareTo(value) < 0) {
			throw new BankingException("Insufficient balance.", HttpStatus.UNPROCESSABLE_ENTITY);
		}

		source.setBalance(source.getBalance().subtract(value));
		destination.setBalance(destination.getBalance().add(value));

		record(source, destination.getId(), "TRANSFER_OUT", value, "Transfer to account " + destination.getId());

		record(destination, source.getId(), "TRANSFER_IN", value, "Transfer from account " + source.getId());
	}

	@Transactional(readOnly = true)
	public List<BankTransaction> history(Long id) {
		getAccount(id);
		return transactions.findByAccountIdOrderByCreatedAtDesc(id);
	}

	private BankAccount lockAccount(Long id) {
		return accounts.findByIdForUpdate(id)
				.orElseThrow(() -> new BankingException("Account not found.", HttpStatus.NOT_FOUND));
	}

	private BigDecimal validateAmount(BigDecimal amount, boolean allowZero) {

		if (amount == null || amount.scale() > 2 || amount.compareTo(BigDecimal.ZERO) < (allowZero ? 0 : 1)) {
			throw new BankingException(allowZero ? "Amount must be zero or positive with at most two decimals."
					: "Amount must be greater than zero with at most two decimals.", HttpStatus.BAD_REQUEST);
		}

		return amount.setScale(2);
	}

	private void record(BankAccount account, Long relatedId, String type, BigDecimal amount, String description) {

		BankTransaction transaction = new BankTransaction(account.getId(), relatedId, type, amount,
				account.getBalance(), description);

		transactions.save(transaction);

		logger.log("account=" + account.getId() + ", type=" + type + ", amount=" + amount + ", balance="
				+ account.getBalance());
	}
}