package com.lockerflow.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void acceptsBase64SecretOfAtLeast32DecodedBytes() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(securityConfig.jwtSecretKey(properties(secret)).getEncoded()).hasSize(32);
    }

    @Test
    void rejectsMissingMalformedAndShortSecrets() {
        assertThatThrownBy(() -> securityConfig.jwtSecretKey(properties(" ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET_BASE64 must be configured");
        assertThatThrownBy(() -> securityConfig.jwtSecretKey(properties("not-base64%%%")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET_BASE64 must be valid Base64");

        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> securityConfig.jwtSecretKey(properties(shortSecret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET_BASE64 must decode to at least 32 bytes");
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, "lockerflow", Duration.ofMinutes(30));
    }
}
