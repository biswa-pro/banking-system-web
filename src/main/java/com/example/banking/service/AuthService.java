
package com.example.banking.service;

import com.example.banking.model.User;
import com.example.banking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public User register(
            String username,
            String password,
            String email) {

        username = username.trim();
        email = email == null ? "" : email.trim();

        if (username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username is required");
        }

        if (email.isBlank() || !email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException(
                    "A valid email address is required");
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException(
                    "Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        User savedUser = userRepository.save(user);

        try {
            emailService.sendWelcomeEmail(savedUser.getEmail());
        } catch (Exception e) {
            // Registration succeeded even if email delivery failed.
            System.err.println(
                    "Welcome email could not be sent: "
                    + e.getMessage());
        }

        return savedUser;
    }
}
