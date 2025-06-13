package com.paymentgateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String tokenType;
    private Long expiresIn;
    private String username;
    private LocalDateTime issuedAt;

    public static AuthResponse success(String token, String username, Long expiresIn) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .username(username)
                .issuedAt(LocalDateTime.now())
                .build();
    }
}