package com.lockerflow.dto.request;

import com.lockerflow.entity.enums.LockerStationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLockerStationStatusRequest(
        @NotNull(message = "status must not be null")
        LockerStationStatus status
) {
}

