package com.lockerflow.controller;

import com.lockerflow.dto.response.ExpirationProcessingResponse;
import com.lockerflow.service.ExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class AdminOperationController {

    private final ExpirationService expirationService;

    @PostMapping("/expiration/run")
    public ResponseEntity<ExpirationProcessingResponse> runExpirationProcessing() {
        return ResponseEntity.ok(
                ExpirationProcessingResponse.from(expirationService.processExpired())
        );
    }
}
