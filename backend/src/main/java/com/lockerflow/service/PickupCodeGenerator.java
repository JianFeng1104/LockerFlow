package com.lockerflow.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;

@Component
public class PickupCodeGenerator {

    private static final int CODE_SPACE = 1_000_000;
    private final SecureRandom secureRandom;

    public PickupCodeGenerator() {
        this(new SecureRandom());
    }

    PickupCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(CODE_SPACE));
    }
}
