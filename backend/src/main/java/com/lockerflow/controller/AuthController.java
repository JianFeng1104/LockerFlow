package com.lockerflow.controller;

import com.lockerflow.dto.request.LoginRequest;
import com.lockerflow.dto.response.AuthResponse;
import com.lockerflow.dto.response.CurrentUserResponse;
import com.lockerflow.security.AuthenticatedUserResolver;
import com.lockerflow.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(@AuthenticationPrincipal Jwt jwt) {
        Long userId = authenticatedUserResolver.requireUserId(jwt);
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }
}
