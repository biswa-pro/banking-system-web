
package com.example.banking.batch;

import com.example.banking.model.BankTransaction;
import com.example.banking.repository.BankTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionReportService {

    private final BankTransactionRepository transactionRepository;
    private final TransactionCsvWriter csvWriter;

    public TransactionReportService(
            BankTransactionRepository transactionRepository,
            TransactionCsvWriter csvWriter) {
        this.transactionRepository = transactionRepository;
        this.csvWriter = csvWriter;
    }

    public int generateReport() throws java.io.IOException {

        List<BankTransaction> transactions =
                transactionRepository.findAll();

        csvWriter.writeTransactions(transactions);

        return transactions.size();
    }
}
