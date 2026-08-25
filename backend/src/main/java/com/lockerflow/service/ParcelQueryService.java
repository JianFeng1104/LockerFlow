package com.lockerflow.service;

import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.UnauthorizedException;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelQueryService {

    private static final String INVALID_AUTHENTICATION = "Invalid authentication";

    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ParcelResponse> getCourierParcels(Long authenticatedCourierId) {
        requireActiveActor(authenticatedCourierId, Role.COURIER);
        return parcelRepository.findByCourierIdOrderByCreatedAtDesc(authenticatedCourierId)
                .stream()
                .map(ParcelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getCustomerParcels(Long authenticatedCustomerId) {
        requireActiveActor(authenticatedCustomerId, Role.CUSTOMER);
        return parcelRepository.findByCustomerIdOrderByCreatedAtDesc(authenticatedCustomerId)
                .stream()
                .map(ParcelResponse::from)
                .toList();
    }

    private void requireActiveActor(Long userId, Role requiredRole) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_AUTHENTICATION));
        if (actor.getRole() != requiredRole || actor.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException(INVALID_AUTHENTICATION);
        }
    }
}
