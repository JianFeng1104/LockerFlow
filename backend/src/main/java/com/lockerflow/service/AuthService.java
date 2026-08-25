package com.lockerflow.service;

import com.lockerflow.dto.request.LoginRequest;
import com.lockerflow.dto.response.AuthResponse;
import com.lockerflow.dto.response.CurrentUserResponse;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.UnauthorizedException;
import com.lockerflow.repository.UserRepository;
import com.lockerflow.security.LockerFlowUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(),
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!(authenticated.getPrincipal() instanceof LockerFlowUserPrincipal principal)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        User user = requireActiveUser(principal.userId());
        JwtTokenService.GeneratedToken token = jwtTokenService.generateToken(principal);
        return new AuthResponse(
                token.accessToken(),
                "Bearer",
                token.expiresAt(),
                CurrentUserResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        return CurrentUserResponse.from(requireActiveUser(userId));
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid authentication"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid authentication");
        }
        return user;
    }
}
