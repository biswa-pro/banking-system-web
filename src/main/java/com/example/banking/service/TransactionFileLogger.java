
package com.example.banking.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

@Component
public class TransactionFileLogger {

    private final Path logPath =
            Path.of("./logs/transactions.log");

    private final Path csvPath =
            Path.of("./logs/transactions.csv");

    public synchronized void log(String message) {
        try {
            Files.createDirectories(logPath.getParent());

            String timestamp = Instant.now().toString();

            // Keep the existing text log.
            Files.writeString(
                    logPath,
                    timestamp + " | " + message
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            // Create the CSV header when the file is first created.
            if (Files.notExists(csvPath)) {
                Files.writeString(
                        csvPath,
                        "timestamp,details"
                                + System.lineSeparator(),
                        StandardOpenOption.CREATE_NEW
                );
            }

            // Escape quotes so the details remain valid CSV.
            String escapedMessage =
                    "\"" + message.replace("\"", "\"\"") + "\"";

            Files.writeString(
                    csvPath,
                    timestamp + "," + escapedMessage
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {
            System.err.println(
                    "Unable to write transaction log: "
                            + e.getMessage()
            );
        }
    }
}
