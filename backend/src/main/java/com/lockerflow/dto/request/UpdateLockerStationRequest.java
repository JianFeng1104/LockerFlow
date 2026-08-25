package com.lockerflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLockerStationRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @NotBlank(message = "address must not be blank")
        @Size(max = 255, message = "address must not exceed 255 characters")
        String address
) {
}

