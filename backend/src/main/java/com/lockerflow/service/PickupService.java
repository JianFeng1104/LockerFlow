package com.lockerflow.service;

import com.lockerflow.dto.request.PickupParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.exception.ResourceNotFoundException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import com.lockerflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PickupService {

    private static final String INVALID_CODE_MESSAGE = "Invalid or expired pickup code";

    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;
    private final PickupCodeRepository pickupCodeRepository;
    private final LockerCellRepository cellRepository;
    private final PickupCodeHasher pickupCodeHasher;
    private final Clock clock;

    @Transactional
    public ParcelResponse pickUp(Long parcelId, Long authenticatedCustomerId, PickupParcelRequest request) {
        Parcel parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parcel %d was not found".formatted(parcelId)
                ));
        User customer = userRepository.findById(authenticatedCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer %d was not found".formatted(authenticatedCustomerId)
                ));

        validateCustomer(customer);
        if (!customer.getId().equals(parcel.getCustomer().getId())) {
            throw new ConflictException("Customer does not own this parcel");
        }
        if (parcel.getStatus() == ParcelStatus.EXPIRED) {
            throw new ConflictException(INVALID_CODE_MESSAGE);
        }
        if (parcel.getStatus() != ParcelStatus.STORED) {
            throw new ConflictException("Parcel is not available for pickup");
        }

        LockerCell cell = parcel.getLockerCell();
        if (cell == null || cell.getStatus() != LockerCellStatus.OCCUPIED) {
            throw new ConflictException("Parcel is not in an occupied locker cell");
        }

        Instant now = clock.instant();
        if (parcel.getExpiresAt() == null || !now.isBefore(parcel.getExpiresAt())) {
            throw new ConflictException(INVALID_CODE_MESSAGE);
        }

        PickupCode pickupCode = pickupCodeRepository
                .findFirstForUpdateByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
                        parcelId,
                        PickupCodeStatus.ACTIVE
                )
                .orElseThrow(() -> new ConflictException(INVALID_CODE_MESSAGE));
        if (!now.isBefore(pickupCode.getExpiresAt())
                || !pickupCodeHasher.matches(request.pickupCode(), pickupCode.getCodeHash())) {
            throw new ConflictException(INVALID_CODE_MESSAGE);
        }

        pickupCode.markUsed(now);
        parcel.pickUp(now);
        cell.changeStatus(LockerCellStatus.AVAILABLE);

        pickupCodeRepository.flush();
        parcelRepository.flush();
        cellRepository.flush();
        return ParcelResponse.from(parcel);
    }

    private void validateCustomer(User customer) {
        if (customer.getRole() != Role.CUSTOMER) {
            throw new ConflictException(
                    "Customer %d must have role CUSTOMER".formatted(customer.getId())
            );
        }
        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Customer %d is not active".formatted(customer.getId()));
        }
    }
}
