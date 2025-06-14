package com.paymentgateway.controller;

import com.paymentgateway.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("service", "payment-gateway");
        healthInfo.put("version", "1.0.0");

        return ResponseEntity.ok(ApiResponse.success("Service is healthy", healthInfo));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<String>> simpleHealthCheck() {
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}