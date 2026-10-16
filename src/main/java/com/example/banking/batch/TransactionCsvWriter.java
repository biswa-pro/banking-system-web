
package com.example.banking.batch;

import com.example.banking.model.BankTransaction;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Component
public class TransactionCsvWriter {

    private final Path reportPath =
            Path.of("reports", "daily-transactions.csv");

    public void writeTransactions(
            List<BankTransaction> transactions) throws IOException {

        Files.createDirectories(reportPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(
                reportPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            writer.write(
                    "id,accountId,relatedAccountId,type,amount,"
                    + "balanceAfter,description,createdAt");
            writer.newLine();

            for (BankTransaction transaction : transactions) {
                writer.write(
                        value(transaction.getId()) + "," +
                        value(transaction.getAccountId()) + "," +
                        value(transaction.getRelatedAccountId()) + "," +
                        csv(transaction.getType()) + "," +
                        value(transaction.getAmount()) + "," +
                        value(transaction.getBalanceAfter()) + "," +
                        csv(transaction.getDescription()) + "," +
                        value(transaction.getCreatedAt())
                );
                writer.newLine();
            }
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
