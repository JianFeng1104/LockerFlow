package com.lockerflow.controller;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.security.AuthenticatedUserResolver;
import com.lockerflow.service.ParcelQueryService;
import com.lockerflow.service.ParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courier/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;
    private final ParcelQueryService parcelQueryService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping
    public List<ParcelResponse> getCurrentCourierParcels(@AuthenticationPrincipal Jwt jwt) {
        Long courierId = authenticatedUserResolver.requireUserId(jwt);
        return parcelQueryService.getCourierParcels(courierId);
    }

    @PostMapping
    public ResponseEntity<StoreParcelResponse> storeParcel(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateParcelRequest request
    ) {
        Long courierId = authenticatedUserResolver.requireUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelService.storeParcel(request, courierId));
    }
}
