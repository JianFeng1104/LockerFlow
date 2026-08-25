package com.lockerflow.security;

import com.lockerflow.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserResolverTests {

    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    @Test
    void resolvesPositiveNumericSubject() {
        Jwt jwt = jwtWithSubject("42");

        assertThat(resolver.requireUserId(jwt)).isEqualTo(42L);
    }

    @Test
    void rejectsMissingJwtOrSubject() {
        Jwt jwt = jwtWithSubject(null);

        assertInvalid(() -> resolver.requireUserId(null));
        assertInvalid(() -> resolver.requireUserId(jwt));
    }

    @Test
    void rejectsNonNumericAndNonPositiveSubjects() {
        assertInvalid(() -> resolver.requireUserId(jwtWithSubject("customer-1")));
        assertInvalid(() -> resolver.requireUserId(jwtWithSubject("0")));
        assertInvalid(() -> resolver.requireUserId(jwtWithSubject("-1")));
    }

    private Jwt jwtWithSubject(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return jwt;
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid authentication");
    }
}
