package com.lockerflow.dto.request;

import com.lockerflow.entity.enums.LockerCellStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLockerCellStatusRequest(
        @NotNull(message = "status must not be null")
        LockerCellStatus status
) {
}

