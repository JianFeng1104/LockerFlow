package com.lockerflow.service;

import com.lockerflow.config.JwtProperties;
import com.lockerflow.config.SecurityConfig;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.security.LockerFlowUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void generatedTokenVerifiesAndContainsRequiredClaimsFromFixedClock() {
        Instant issuedAt = Instant.now().minusSeconds(5);
        JwtFixture fixture = fixture("lockerflow", issuedAt, Duration.ofMinutes(30));

        JwtTokenService.GeneratedToken generated = fixture.service().generateToken(principal());
        Jwt decoded = fixture.decoder().decode(generated.accessToken());

        assertThat(generated.expiresAt()).isEqualTo(issuedAt.plus(Duration.ofMinutes(30)));
        assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("lockerflow");
        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getIssuedAt()).isEqualTo(issuedAt.truncatedTo(ChronoUnit.SECONDS));
        assertThat(decoded.getExpiresAt()).isEqualTo(
                issuedAt.plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS)
        );
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("COURIER");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("courier.jwt");
        assertThat(decoded.getId()).isNotBlank();
    }

    @Test
    void decoderRejectsExpiredWrongIssuerAndTamperedTokens() {
        Instant now = Instant.now();
        JwtFixture expected = fixture("lockerflow", now, Duration.ofMinutes(30));
        String expired = fixture("lockerflow", now.minus(Duration.ofHours(2)), Duration.ofMinutes(30))
                .service().generateToken(principal()).accessToken();
        String wrongIssuer = fixture("other-issuer", now, Duration.ofMinutes(30))
                .service().generateToken(principal()).accessToken();
        String valid = expected.service().generateToken(principal()).accessToken();

        assertThatThrownBy(() -> expected.decoder().decode(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> expected.decoder().decode(wrongIssuer)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> expected.decoder().decode(tamper(valid))).isInstanceOf(JwtException.class);
    }

    private JwtFixture fixture(String issuer, Instant issuedAt, Duration ttl) {
        SecurityConfig config = new SecurityConfig();
        JwtProperties properties = new JwtProperties(SECRET, issuer, ttl);
        SecretKey key = config.jwtSecretKey(properties);
        JwtDecoder decoder = config.jwtDecoder(key, new JwtProperties(SECRET, "lockerflow", ttl));
        JwtTokenService service = new JwtTokenService(
                config.jwtEncoder(key),
                properties,
                Clock.fixed(issuedAt, ZoneOffset.UTC)
        );
        return new JwtFixture(service, decoder);
    }

    private LockerFlowUserPrincipal principal() {
        return new LockerFlowUserPrincipal(42L, "courier.jwt", "bcrypt-hash", Role.COURIER, true);
    }

    private String tamper(String token) {
        char last = token.charAt(token.length() - 1);
        return token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
    }

    private record JwtFixture(JwtTokenService service, JwtDecoder decoder) {
    }
}
