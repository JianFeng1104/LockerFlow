package com.lockerflow.controller;

import com.lockerflow.dto.request.PickupParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.security.AuthenticatedUserResolver;
import com.lockerflow.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/parcels")
@RequiredArgsConstructor
public class PickupController {

    private final PickupService pickupService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @PostMapping("/{parcelId}/pickup")
    public ResponseEntity<ParcelResponse> pickUp(
            @PathVariable Long parcelId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PickupParcelRequest request
    ) {
        Long customerId = authenticatedUserResolver.requireUserId(jwt);
        return ResponseEntity.ok(pickupService.pickUp(parcelId, customerId, request));
    }
}
