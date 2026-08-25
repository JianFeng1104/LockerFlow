package com.lockerflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void loginReturnsHs256TokenWithRequiredClaimsAndMeReturnsCurrentUser() throws Exception {
        User courier = saveUser("auth.courier", "correct-password", Role.COURIER, 1);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "  AUTH.COURIER  ",
                                "password", "correct-password"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.user.id").value(courier.getId()))
                .andExpect(jsonPath("$.user.username").value("auth.courier"))
                .andExpect(jsonPath("$.user.role").value("COURIER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        String tokenValue = response.get("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(tokenValue);
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("lockerflow");
        assertThat(jwt.getSubject()).isEqualTo(courier.getId().toString());
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(30 * 60));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("COURIER");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("auth.courier");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courier.getId()))
                .andExpect(jsonPath("$.username").value("auth.courier"))
                .andExpect(jsonPath("$.role").value("COURIER"));
    }

    @Test
    void unknownWrongPasswordAndDisabledUserShareGeneric401() throws Exception {
        saveUser("auth.active", "correct-password", Role.CUSTOMER, 2);
        User disabled = saveUser("auth.disabled", "correct-password", Role.CUSTOMER, 3);
        disabled.changeStatus(UserStatus.DISABLED);
        userRepository.flush();

        assertInvalidLogin("missing-user", "correct-password");
        assertInvalidLogin("auth.active", "wrong-password");
        assertInvalidLogin("auth.disabled", "correct-password");
    }

    @Test
    void validatesLoginRequestBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", " ", "password", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void protectedEndpointReturnsJson401ForMissingMalformedAndInvalidJwt() throws Exception {
        assertUnauthorized(null);
        assertUnauthorized("not-a-jwt");

        Instant now = Instant.now();
        String valid = encode("lockerflow", "1", now.minusSeconds(30), now.plusSeconds(300), null);
        assertUnauthorized(tamper(valid));
        assertUnauthorized(encode("lockerflow", "1", now.minusSeconds(600), now.minusSeconds(300), null));
        assertUnauthorized(encode("wrong-issuer", "1", now.minusSeconds(30), now.plusSeconds(300), null));
        assertUnauthorized(encode("lockerflow", "1", now, now.plusSeconds(600), now.plusSeconds(300)));
    }

    @Test
    void meRejectsValidTokenWhenDatabaseUserIsMissingOrDisabled() throws Exception {
        Instant now = Instant.now();
        assertInvalidAuthentication(encode("lockerflow", null, now, now.plusSeconds(300), null));
        assertInvalidAuthentication(encode("lockerflow", "not-a-user-id", now, now.plusSeconds(300), null));

        String missingUserToken = encode("lockerflow", "999999", now, now.plusSeconds(300), null);
        assertInvalidAuthentication(missingUserToken);

        User disabled = saveUser("auth.me.disabled", "password", Role.ADMIN, 4);
        disabled.changeStatus(UserStatus.DISABLED);
        userRepository.flush();
        String disabledToken = encode(
                "lockerflow", disabled.getId().toString(), now, now.plusSeconds(300), null
        );
        assertInvalidAuthentication(disabledToken);
    }

    private void assertInvalidAuthentication(String token) throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication"));
    }

    private void assertInvalidLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    private void assertUnauthorized(String token) throws Exception {
        var request = get("/api/auth/me");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    private String encode(
            String issuer,
            String subject,
            Instant issuedAt,
            Instant expiresAt,
            Instant notBefore
    ) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("CUSTOMER"))
                .claim("username", "jwt-test");
        if (subject != null) {
            claims.subject(subject);
        }
        if (notBefore != null) {
            claims.notBefore(notBefore);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();
    }

    private String tamper(String token) {
        char last = token.charAt(token.length() - 1);
        return token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
    }

    private User saveUser(String username, String password, Role role, int suffix) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                String.format("136%08d", suffix),
                passwordEncoder.encode(password),
                role
        ));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
