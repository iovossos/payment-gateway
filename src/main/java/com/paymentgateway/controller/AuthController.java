package com.paymentgateway.controller;

import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.ApiResponse;
import com.paymentgateway.model.dto.AuthRequest;
import com.paymentgateway.model.dto.AuthResponse;
import com.paymentgateway.model.dto.UserRegistrationRequest;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        try {
            log.info("Login attempt for username: {}", request.getUsername());

            AuthResponse authResponse = authService.authenticate(request);

            log.info("Login successful for username: {}", request.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));

        } catch (PaymentException e) {
            log.warn("Login failed for username: {}, reason: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during login for username: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody UserRegistrationRequest request) {
        try {
            log.info("Registration attempt for username: {}", request.getUsername());

            User user = authService.registerUser(request);

            log.info("Registration successful for username: {}", request.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User registered successfully", user));

        } catch (PaymentException e) {
            log.warn("Registration failed for username: {}, reason: {}", request.getUsername(), e.getMessage());

            // Handle specific registration errors
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(e.getMessage()));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during registration for username: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            log.info("Logout for username: {}", username);

            // In real implementation we should
            // invalidate the JWT token (add to blacklist)
            // clear any cached user sessions
            // log the logout event

            return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));

        } catch (Exception e) {
            log.error("Error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            String username = authentication.getName();
            User user = authService.findByUsername(username)
                    .orElseThrow(() -> new PaymentException("User not found"));

            return ResponseEntity.ok(ApiResponse.success(user));

        } catch (PaymentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred"));
        }
    }

    @PostMapping("/validate-token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestParam String token) {
        try {
            // Add null/empty check
            if (token == null || token.trim().isEmpty()) {
                log.debug("Token validation failed - empty token");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Token cannot be empty"));
            }

            boolean isValid = authService.validateToken(token);

            if (isValid) {
                String username = authService.getUsernameFromToken(token);
                log.debug("Token validation successful for user: {}", username);
                return ResponseEntity.ok(ApiResponse.success("Token is valid", true));
            } else {
                log.debug("Token validation failed - invalid token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.success("Token is invalid", false));
            }

        } catch (Exception e) {
            log.error("Error during token validation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Token validation failed: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        try {
            // In real implementation we should
            // validate the refresh token
            // generate a new access token
            // optionally rotate the refresh token

            String username = authentication.getName();
            AuthRequest refreshRequest = AuthRequest.builder()
                    .username(username)
                    .password("") // Password not needed for refresh
                    .build();

            // we just generate a new token for simplicity's sake
            AuthResponse authResponse = authService.authenticate(refreshRequest);

            return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));

        } catch (Exception e) {
            log.error("Error during token refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Token refresh failed"));
        }
    }
}