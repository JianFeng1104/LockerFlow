package com.lockerflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickupCodeHasherTests {

    private final PickupCodeHasher hasher = new PickupCodeHasher();

    @Test
    void hashDoesNotStoreRawCode() {
        String hash = hasher.hash("004271");

        assertThat(hash).isNotEqualTo("004271");
        assertThat(hash).startsWith("$2");
    }

    @Test
    void matchesCorrectCode() {
        String hash = hasher.hash("004271");

        assertThat(hasher.matches("004271", hash)).isTrue();
    }

    @Test
    void rejectsIncorrectCode() {
        String hash = hasher.hash("004271");

        assertThat(hasher.matches("999999", hash)).isFalse();
    }
}
