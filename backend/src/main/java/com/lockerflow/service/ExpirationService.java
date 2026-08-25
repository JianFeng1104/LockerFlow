package com.lockerflow.service;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpirationService {

    private final ParcelRepository parcelRepository;
    private final PickupCodeRepository pickupCodeRepository;
    private final Clock clock;

    @Transactional
    public ExpirationResult processExpired() {
        Instant cutoff = clock.instant();
        List<Parcel> parcels = parcelRepository
                .findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        ParcelStatus.STORED,
                        cutoff
                );
        parcels.forEach(Parcel::expire);

        List<PickupCode> pickupCodes = pickupCodeRepository
                .findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        PickupCodeStatus.ACTIVE,
                        cutoff
                );
        pickupCodes.forEach(PickupCode::markExpired);

        parcelRepository.flush();
        pickupCodeRepository.flush();
        return new ExpirationResult(cutoff, parcels.size(), pickupCodes.size());
    }
}
