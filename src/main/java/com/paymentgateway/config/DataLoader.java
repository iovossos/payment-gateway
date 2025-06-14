package com.paymentgateway.config;

import com.paymentgateway.model.entity.User;
import com.paymentgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            createTestUsers();
        }
    }

    private void createTestUsers() {
        log.info("Creating test users...");

        User testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .firstName("Test")
                .lastName("User")
                .phoneNumber("1234567890")
                .active(true)
                .build();

        User adminUser = User.builder()
                .username("admin")
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("admin123"))
                .firstName("Admin")
                .lastName("User")
                .phoneNumber("0987654321")
                .active(true)
                .build();

        userRepository.save(testUser);
        userRepository.save(adminUser);

        log.info("Test users created:");
        log.info("Username: testuser, Password: password123");
        log.info("Username: admin, Password: admin123");
    }
}