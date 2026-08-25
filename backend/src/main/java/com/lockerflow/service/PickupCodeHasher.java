package com.lockerflow.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PickupCodeHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawCode) {
        return encoder.encode(rawCode);
    }

    public boolean matches(String rawCode, String codeHash) {
        return encoder.matches(rawCode, codeHash);
    }
}
