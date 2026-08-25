package com.lockerflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "lockerflow.security.jwt")
@Validated
public record JwtProperties(
        @NotBlank
        String secretBase64,

        @NotBlank
        String issuer,

        @NotNull
        Duration accessTokenTtl
) {
    public JwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
            throw new IllegalArgumentException("access-token-ttl must be positive");
        }
    }
}
