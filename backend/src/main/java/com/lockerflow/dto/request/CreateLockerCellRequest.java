package com.lockerflow.dto.request;

import com.lockerflow.entity.enums.LockerSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLockerCellRequest(
        @NotBlank(message = "cellCode must not be blank")
        @Size(max = 20, message = "cellCode must not exceed 20 characters")
        String cellCode,

        @NotNull(message = "size must not be null")
        LockerSize size
) {
}

