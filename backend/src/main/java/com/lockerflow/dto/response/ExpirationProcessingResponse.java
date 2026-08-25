package com.lockerflow.dto.response;

import com.lockerflow.service.ExpirationResult;

import java.time.Instant;

public record ExpirationProcessingResponse(
        Instant processedAt,
        int expiredParcels,
        int expiredPickupCodes
) {
    public static ExpirationProcessingResponse from(ExpirationResult result) {
        return new ExpirationProcessingResponse(
                result.processedAt(),
                result.expiredParcels(),
                result.expiredPickupCodes()
        );
    }
}
