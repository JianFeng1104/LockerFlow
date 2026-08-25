package com.lockerflow.service;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.repository.PickupCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PickupCodeService {

    private final PickupCodeGenerator generator;
    private final PickupCodeHasher hasher;
    private final PickupCodeRepository pickupCodeRepository;

    @Transactional
    public IssuedPickupCode issueFor(Parcel parcel) {
        if (parcel == null || parcel.getStatus() != ParcelStatus.STORED) {
            throw new ConflictException("Pickup codes can only be issued for stored parcels");
        }
        if (parcel.getExpiresAt() == null) {
            throw new ConflictException("Stored parcel must have an expiration time");
        }

        String rawCode = generator.generate();
        PickupCode pickupCode = new PickupCode(parcel, hasher.hash(rawCode), parcel.getExpiresAt());
        pickupCodeRepository.saveAndFlush(pickupCode);
        return new IssuedPickupCode(rawCode, parcel.getExpiresAt());
    }

    public record IssuedPickupCode(String rawCode, Instant expiresAt) {
    }
}
