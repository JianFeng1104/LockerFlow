package com.lockerflow.dto.response;

import java.time.Instant;

public record StoreParcelResponse(
        ParcelResponse parcel,
        String pickupCode,
        Instant pickupCodeExpiresAt
) {
}
