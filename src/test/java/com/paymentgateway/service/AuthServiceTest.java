package com.paymentgateway.service;

import com.paymentgateway.exception.PaymentException;
import com.paymentgateway.model.dto.AuthRequest;
import com.paymentgateway.model.dto.AuthResponse;
import com.paymentgateway.model.dto.UserRegistrationRequest;
import com.paymentgateway.model.entity.User;
import com.paymentgateway.repository.UserRepository;
import com.paymentgateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private AuthRequest authRequest;
    private UserRegistrationRequest registrationRequest;
    private User user;

    @BeforeEach
    void setUp() {
        authRequest = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        registrationRequest = UserRegistrationRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("1234567890")
                .build();

        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void authenticate_ValidCredentials_ReturnsAuthResponse() {
        // Given
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(jwtTokenProvider.generateToken("testuser")).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationTime()).thenReturn(3600L);

        // When
        AuthResponse response = authService.authenticate(authRequest);

        // Then
        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("testuser", response.getUsername());
        assertEquals(3600L, response.getExpiresIn());
        assertNotNull(response.getIssuedAt());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider).generateToken("testuser");
    }

    @Test
    void authenticate_InvalidCredentials_ThrowsPaymentException() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> authService.authenticate(authRequest));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    void authenticate_NullRequest_ThrowsPaymentException() {
        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> authService.authenticate(null));

        assertEquals("Authentication request cannot be null", exception.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void registerUser_ValidRequest_ReturnsUser() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        User result = authService.registerUser(registrationRequest);

        // Then
        assertNotNull(result);
        assertEquals(user.getId(), result.getId());
        assertEquals(user.getUsername(), result.getUsername());

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("newuser@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_DuplicateUsername_ThrowsPaymentException() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> authService.registerUser(registrationRequest));

        assertEquals("Username already exists", exception.getMessage());
        verify(userRepository).existsByUsername("newuser");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_DuplicateEmail_ThrowsPaymentException() {
        // Given
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> authService.registerUser(registrationRequest));

        assertEquals("Email already exists", exception.getMessage());
        verify(userRepository).existsByEmail("newuser@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_NullRequest_ThrowsPaymentException() {
        // When & Then
        PaymentException exception = assertThrows(PaymentException.class,
                () -> authService.registerUser(null));

        assertEquals("Registration request cannot be null", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void findByUsername_ExistingUser_ReturnsUser() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // When
        Optional<User> result = authService.findByUsername("testuser");

        // Then
        assertTrue(result.isPresent());
        assertEquals(user.getUsername(), result.get().getUsername());
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void findByUsername_NonExistingUser_ReturnsEmpty() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When
        Optional<User> result = authService.findByUsername("nonexistent");

        // Then
        assertFalse(result.isPresent());
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        // Given
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);

        // When
        boolean result = authService.validateToken("valid-token");

        // Then
        assertTrue(result);
        verify(jwtTokenProvider).validateToken("valid-token");
    }

    @Test
    void validateToken_InvalidToken_ReturnsFalse() {
        // Given
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        // When
        boolean result = authService.validateToken("invalid-token");

        // Then
        assertFalse(result);
        verify(jwtTokenProvider).validateToken("invalid-token");
    }

    @Test
    void getUsernameFromToken_ValidToken_ReturnsUsername() {
        // Given
        when(jwtTokenProvider.getUsernameFromToken("valid-token")).thenReturn("testuser");

        // When
        String username = authService.getUsernameFromToken("valid-token");

        // Then
        assertEquals("testuser", username);
        verify(jwtTokenProvider).getUsernameFromToken("valid-token");
    }
}