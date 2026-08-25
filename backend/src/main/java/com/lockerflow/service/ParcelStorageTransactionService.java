package com.lockerflow.service;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.exception.ResourceNotFoundException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ParcelStorageTransactionService {

    private static final Duration DEFAULT_STORAGE_DURATION = Duration.ofHours(48);

    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;
    private final LockerStationRepository stationRepository;
    private final LockerCellRepository cellRepository;
    private final LockerAllocationService allocationService;
    private final PickupCodeService pickupCodeService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoreParcelResponse storeParcel(CreateParcelRequest request, Long authenticatedCourierId) {
        String trackingNumber = normalizeTrackingNumber(request.trackingNumber());
        if (parcelRepository.existsByTrackingNumberIgnoreCase(trackingNumber)) {
            throw new ConflictException(
                    "Parcel tracking number %s already exists".formatted(trackingNumber)
            );
        }

        User customer = requireUser(request.customerId(), "Customer");
        validateUser(customer, Role.CUSTOMER, "Customer");

        User courier = requireUser(authenticatedCourierId, "Courier");
        validateUser(courier, Role.COURIER, "Courier");

        LockerStation station = stationRepository.findById(request.stationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Locker station %d was not found".formatted(request.stationId())
                ));
        if (station.getStatus() != LockerStationStatus.ACTIVE) {
            throw new ConflictException("Locker station is not available for parcel storage");
        }

        LockerCell cell = allocationService.allocate(station, request.size());
        if (cell.getStatus() != LockerCellStatus.AVAILABLE) {
            throw new ConflictException("Selected locker cell is no longer available");
        }

        Instant storedAt = clock.instant();
        Instant expiresAt = storedAt.plus(DEFAULT_STORAGE_DURATION);
        Parcel parcel = new Parcel(trackingNumber, customer, courier, request.size());
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        parcel.storeIn(cell, storedAt, expiresAt);

        Parcel saved = parcelRepository.save(parcel);
        PickupCodeService.IssuedPickupCode issuedCode = pickupCodeService.issueFor(saved);
        cellRepository.flush();
        parcelRepository.flush();
        return new StoreParcelResponse(
                ParcelResponse.from(saved),
                issuedCode.rawCode(),
                issuedCode.expiresAt()
        );
    }

    private User requireUser(Long userId, String userType) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "%s %d was not found".formatted(userType, userId)
                ));
    }

    private void validateUser(User user, Role requiredRole, String userType) {
        if (user.getRole() != requiredRole) {
            throw new ConflictException(
                    "%s %d must have role %s".formatted(userType, user.getId(), requiredRole)
            );
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException(
                    "%s %d is not active".formatted(userType, user.getId())
            );
        }
    }

    private String normalizeTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        return trackingNumber.trim().toUpperCase(Locale.ROOT);
    }
}
