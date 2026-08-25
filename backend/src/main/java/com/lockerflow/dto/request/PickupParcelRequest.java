package com.lockerflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PickupParcelRequest(
        @NotBlank(message = "pickupCode must not be blank")
        @Pattern(regexp = "\\d{6}", message = "pickupCode must contain exactly 6 digits")
        String pickupCode
) {
}
