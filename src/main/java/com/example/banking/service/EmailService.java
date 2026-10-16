
package com.example.banking.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(
            String recipient,
            String subject,
            String body) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }

    public void sendWelcomeEmail(String email) {
        sendEmail(
            email,
            "Welcome to Banking System",
            "Your registration was successful. "
            + "Welcome to our Banking System!"
        );
    }

    public void sendLoginAlert(String email) {
        sendEmail(
            email,
            "Banking System Login Alert",
            "A successful login to your Banking System account "
            + "was detected."
        );
    }

    public void sendTransactionAlert(
            String email,
            String operation,
            double amount,
            double balance) {

        String body = String.format(
            "Your banking transaction was successful.%n"
            + "Operation: %s%n"
            + "Amount: %.2f%n"
            + "Updated balance: %.2f%n",
            operation,
            amount,
            balance
        );

        sendEmail(
            email,
            "Banking System Transaction Alert",
            body
        );
    }
}
