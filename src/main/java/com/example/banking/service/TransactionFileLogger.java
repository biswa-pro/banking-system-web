package com.example.banking.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

@Component
public class TransactionFileLogger {

    private final Path path =
            Path.of("./logs/transactions.log");

    public synchronized void log(String message) {
        try {
            Files.createDirectories(path.getParent());

            Files.writeString(
                path,
                Instant.now() + " | " + message
                    + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // Keep the database transaction history as the source of truth.
            System.err.println("Unable to write transaction log: "
                    + e.getMessage());
        }
    }
}