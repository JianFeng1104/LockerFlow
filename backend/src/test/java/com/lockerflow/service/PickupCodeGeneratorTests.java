package com.lockerflow.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PickupCodeGeneratorTests {

    @Test
    void generatesExactlySixNumericCharacters() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(482913);

        String code = new PickupCodeGenerator(random).generate();

        assertThat(code).matches("\\d{6}");
        verify(random).nextInt(1_000_000);
    }

    @Test
    void preservesLeadingZeroes() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(42);

        assertThat(new PickupCodeGenerator(random).generate()).isEqualTo("000042");
    }

    @Test
    void formatsBothEndsOfAllowedRange() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(0, 999999);
        PickupCodeGenerator generator = new PickupCodeGenerator(random);

        assertThat(generator.generate()).isEqualTo("000000");
        assertThat(generator.generate()).isEqualTo("999999");
    }
}
