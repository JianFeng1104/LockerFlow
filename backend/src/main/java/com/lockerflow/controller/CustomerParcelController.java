package com.lockerflow.controller;

import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.security.AuthenticatedUserResolver;
import com.lockerflow.service.ParcelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/parcels")
@RequiredArgsConstructor
public class CustomerParcelController {

    private final ParcelQueryService parcelQueryService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping
    public List<ParcelResponse> getCurrentCustomerParcels(@AuthenticationPrincipal Jwt jwt) {
        Long customerId = authenticatedUserResolver.requireUserId(jwt);
        return parcelQueryService.getCustomerParcels(customerId);
    }
}
