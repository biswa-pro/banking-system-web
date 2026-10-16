
package com.example.banking.controller;

import com.example.banking.model.User;
import com.example.banking.dto.LoginRequest;
import com.example.banking.dto.LoginResponse;
import com.example.banking.dto.RegisterRequest;
import com.example.banking.repository.UserRepository;
import com.example.banking.service.AuthService;
import com.example.banking.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AuthService authService,
            EmailService emailService,
            UserRepository userRepository) {

        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.authService = authService;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request) {

        try {
            User user = authService.register(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(java.util.Map.of(
                            "message", "Account created successfully",
                            "username", user.getUsername(),
                            "role", user.getRole()
                    ));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of(
                            "message", ex.getMessage()
                    ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(),
                                    request.getPassword()
                            )
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(
                    context, httpRequest, httpResponse
            );

            // Send an email alert after successful authentication.
            try {
                userRepository.findByUsername(
                        authentication.getName()
                ).ifPresent(user -> {
                    if (user.getEmail() != null
                            && !user.getEmail().isBlank()) {
                        emailService.sendLoginAlert(user.getEmail());
                    }
                });
            } catch (Exception e) {
                // An email failure should not cancel a successful login.
                System.err.println(
                        "Login alert email could not be sent: "
                                + e.getMessage()
                );
            }

            String role = authentication.getAuthorities()
                    .stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority())
                    .orElse("ROLE_USER");

            LoginResponse response = new LoginResponse(
                    "Login successful",
                    authentication.getName(),
                    role
            );

            return ResponseEntity.ok(response);

        } catch (
                org.springframework.security.core.AuthenticationException ex) {

            SecurityContextHolder.clearContext();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of(
                            "message", "Invalid username or password"
                    ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        SecurityContextHolder.clearContext();

        jakarta.servlet.http.HttpSession session =
                request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                java.util.Map.of(
                        "message", "Logout successful"
                )
        );
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(
            HttpServletRequest request) {

        org.springframework.security.web.csrf.CsrfToken token =
                (org.springframework.security.web.csrf.CsrfToken)
                        request.getAttribute("_csrf");

        if (token == null) {
            return ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of(
                            "message", "CSRF token unavailable"
                    ));
        }

        return ResponseEntity.ok(
                java.util.Map.of("token", token.getToken())
        );
    }
}
