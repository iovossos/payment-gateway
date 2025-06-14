package com.paymentgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.config.TestSecurityConfig;
import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.ApiResponse;
import com.paymentgateway.model.dto.AuthRequest;
import com.paymentgateway.model.dto.AuthResponse;
import com.paymentgateway.model.dto.UserRegistrationRequest;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private AuthRequest validAuthRequest;
    private UserRegistrationRequest validRegistrationRequest;
    private AuthResponse mockAuthResponse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        validAuthRequest = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        validRegistrationRequest = UserRegistrationRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("1234567890")
                .build();

        mockAuthResponse = AuthResponse.builder()
                .token("jwt-token-12345")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .username("testuser")
                .issuedAt(LocalDateTime.now())
                .build();

        mockUser = User.builder()
                .id(1L)
                .username("newuser")
                .email("newuser@example.com")
                .firstName("John")
                .lastName("Doe")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void login_ValidCredentials_ReturnsSuccessResponse() throws Exception {
        // Given
        when(authService.authenticate(any(AuthRequest.class))).thenReturn(mockAuthResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAuthRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token-12345"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void login_InvalidCredentials_ReturnsErrorResponse() throws Exception {
        // Given
        when(authService.authenticate(any(AuthRequest.class)))
                .thenThrow(new PaymentException("Invalid username or password"));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAuthRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_InvalidRequestBody_ReturnsBadRequest() throws Exception {
        // Given - invalid request with missing fields
        AuthRequest invalidRequest = AuthRequest.builder()
                .username("") // Empty username
                .password("") // Empty password
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // Given
        when(authService.registerUser(any(UserRegistrationRequest.class))).thenReturn(mockUser);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistrationRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.email").value("newuser@example.com"));
    }

    @Test
    void register_DuplicateUsername_ReturnsConflictResponse() throws Exception {
        // Given
        when(authService.registerUser(any(UserRegistrationRequest.class)))
                .thenThrow(new PaymentException("Username already exists"));

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistrationRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void register_InvalidEmail_ReturnsBadRequest() throws Exception {
        // Given - invalid email format
        UserRegistrationRequest invalidRequest = UserRegistrationRequest.builder()
                .username("testuser")
                .email("invalid-email") // Invalid email
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_MissingRequiredFields_ReturnsBadRequest() throws Exception {
        // Given - missing required fields
        UserRegistrationRequest invalidRequest = UserRegistrationRequest.builder()
                .username("") // Empty username
                .email("test@example.com")
                .password("") // Empty password
                .firstName("")
                .lastName("")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    void logout_AuthenticatedUser_ReturnsSuccessResponse() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void logout_UnauthenticatedUser_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}