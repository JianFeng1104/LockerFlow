package com.lockerflow.dto.request;

import com.lockerflow.entity.enums.LockerSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateParcelRequest(
        @NotBlank(message = "trackingNumber must not be blank")
        @Size(max = 64, message = "trackingNumber must not exceed 64 characters")
        String trackingNumber,

        @NotNull(message = "customerId must not be null")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @NotNull(message = "stationId must not be null")
        @Positive(message = "stationId must be positive")
        Long stationId,

        @NotNull(message = "size must not be null")
        LockerSize size
) {
}
