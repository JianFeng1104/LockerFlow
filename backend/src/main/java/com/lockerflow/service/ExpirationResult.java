package com.lockerflow.service;

import java.time.Instant;

public record ExpirationResult(
        Instant processedAt,
        int expiredParcels,
        int expiredPickupCodes
) {
}
