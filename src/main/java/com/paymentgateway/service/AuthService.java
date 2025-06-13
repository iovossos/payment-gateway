package com.paymentgateway.service;

import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.AuthRequest;
import com.paymentgateway.model.dto.AuthResponse;
import com.paymentgateway.model.dto.UserRegistrationRequest;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.repository.UserRepository;
import com.paymentgateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        if (request == null) {
            throw new PaymentException("Authentication request cannot be null");
        }

        try {
            log.info("Attempting authentication for user: {}", request.getUsername());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            String username = authentication.getName();
            String token = jwtTokenProvider.generateToken(username);
            Long expiresIn = jwtTokenProvider.getExpirationTime();

            log.info("Authentication successful for user: {}", username);

            return AuthResponse.success(token, username, expiresIn);

        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for user: {}", request.getUsername());
            throw new PaymentException("Invalid username or password");
        } catch (AuthenticationException e) {
            log.error("Authentication error for user: {}", request.getUsername(), e);
            throw new PaymentException("Authentication failed");
        }
    }

    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        if (request == null) {
            throw new PaymentException("Registration request cannot be null");
        }

        log.info("Attempting to register user: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed - username already exists: {}", request.getUsername());
            throw new PaymentException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed - email already exists: {}", request.getEmail());
            throw new PaymentException("Email already exists");
        }

        try {
            // Create new user
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .active(true)
                    .build();

            User savedUser = userRepository.save(user);
            log.info("User registered successfully: {}", savedUser.getUsername());

            return savedUser;

        } catch (Exception e) {
            log.error("Error registering user: {}", request.getUsername(), e);
            throw new PaymentException("Failed to register user");
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
            return Optional.empty();
        }

        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
    }

    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            return jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        try {
            return jwtTokenProvider.getUsernameFromToken(token);
        } catch (Exception e) {
            log.warn("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void deactivateUser(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found: " + username));

        user.setActive(false);
        userRepository.save(user);

        log.info("User deactivated: {}", username);
    }

    @Transactional
    public void activateUser(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new PaymentException("User not found: " + username));

        user.setActive(true);
        userRepository.save(user);

        log.info("User activated: {}", username);
    }

    @Transactional(readOnly = true)
    public boolean isUserActive(String username) {
        return findByUsername(username)
                .map(User::getActive)
                .orElse(false);
    }
}