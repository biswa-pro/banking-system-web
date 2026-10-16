
package com.example.banking.batch;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class BatchReportApplication {

    public static void main(String[] args) {

        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(
                        com.example.banking.BankingSystemApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(
                                "spring.main.web-application-type=servlet",
                                "server.port=0")
                        .run(args)) {

            TransactionReportService reportService =
                    context.getBean(TransactionReportService.class);

            int count = reportService.generateReport();

            System.out.println(
                    "Batch report completed successfully. Records processed: "
                            + count);

        } catch (Exception e) {
            System.err.println("Batch report failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
