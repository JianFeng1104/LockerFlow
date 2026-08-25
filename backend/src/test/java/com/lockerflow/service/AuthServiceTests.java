package com.lockerflow.service;

import com.lockerflow.dto.request.LoginRequest;
import com.lockerflow.dto.response.AuthResponse;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.UnauthorizedException;
import com.lockerflow.repository.UserRepository;
import com.lockerflow.security.LockerFlowUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-23T10:30:00Z");

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserRepository userRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtTokenService, userRepository);
    }

    @Test
    void validCredentialsReturnGeneratedTokenAndActiveUser() {
        LockerFlowUserPrincipal principal = new LockerFlowUserPrincipal(
                7L, "courier.a", "bcrypt-hash", Role.COURIER, true
        );
        Authentication authentication = mock(Authentication.class);
        User user = user(UserStatus.ACTIVE);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(authentication);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtTokenService.generateToken(principal)).thenReturn(
                new JwtTokenService.GeneratedToken("signed-token", EXPIRES_AT)
        );

        AuthResponse response = authService.login(new LoginRequest("  courier.a  ", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(response.user().role()).isEqualTo(Role.COURIER);

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        UsernamePasswordAuthenticationToken credentials =
                (UsernamePasswordAuthenticationToken) captor.getValue();
        assertThat(credentials.getName()).isEqualTo("courier.a");
        assertThat(credentials.getCredentials()).isEqualTo("correct-password");
    }

    @Test
    void wrongPasswordAndUnknownUsernameReturnSameUnauthorizedMessage() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadCredentialsException("internal detail"));

        assertGenericInvalidCredentials(new LoginRequest("courier.a", "wrong-password"));
        assertGenericInvalidCredentials(new LoginRequest("missing-user", "password"));
    }

    @Test
    void disabledAuthenticationReturnsGenericUnauthorizedMessage() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DisabledException("disabled"));

        assertGenericInvalidCredentials(new LoginRequest("disabled-user", "password"));
    }

    @Test
    void databaseStatusIsRecheckedBeforeReturningTokenOrCurrentUser() {
        LockerFlowUserPrincipal principal = new LockerFlowUserPrincipal(
                7L, "customer.a", "bcrypt-hash", Role.CUSTOMER, true
        );
        Authentication authentication = mock(Authentication.class);
        User disabled = user(UserStatus.DISABLED);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(authentication);
        when(userRepository.findById(7L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer.a", "password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid authentication");
        assertThatThrownBy(() -> authService.getCurrentUser(7L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid authentication");
    }

    private void assertGenericInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    private User user(UserStatus status) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(7L);
        lenient().when(user.getUsername()).thenReturn("courier.a");
        lenient().when(user.getEmail()).thenReturn("courier.a@example.com");
        lenient().when(user.getPhone()).thenReturn("13600000007");
        lenient().when(user.getRole()).thenReturn(Role.COURIER);
        lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }
}
