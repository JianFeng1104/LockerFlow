package com.lockerflow.service;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private PickupCodeRepository pickupCodeRepository;

    private ExpirationService service;

    @BeforeEach
    void setUp() {
        service = new ExpirationService(
                parcelRepository,
                pickupCodeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void expiresEveryEligibleEntityAndReturnsFixedClockStatistics() {
        Parcel firstParcel = mock(Parcel.class);
        Parcel secondParcel = mock(Parcel.class);
        PickupCode firstCode = mock(PickupCode.class);
        PickupCode secondCode = mock(PickupCode.class);
        when(parcelRepository.findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                ParcelStatus.STORED, NOW
        )).thenReturn(List.of(firstParcel, secondParcel));
        when(pickupCodeRepository.findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                PickupCodeStatus.ACTIVE, NOW
        )).thenReturn(List.of(firstCode, secondCode));

        ExpirationResult result = service.processExpired();

        assertThat(result.processedAt()).isEqualTo(NOW);
        assertThat(result.expiredParcels()).isEqualTo(2);
        assertThat(result.expiredPickupCodes()).isEqualTo(2);
        verify(firstParcel).expire();
        verify(secondParcel).expire();
        verify(firstCode).markExpired();
        verify(secondCode).markExpired();
        InOrder flushOrder = inOrder(parcelRepository, pickupCodeRepository);
        flushOrder.verify(parcelRepository).flush();
        flushOrder.verify(pickupCodeRepository).flush();
    }

    @Test
    void repeatedProcessingIsNaturallyIdempotent() {
        Parcel parcel = mock(Parcel.class);
        PickupCode code = mock(PickupCode.class);
        when(parcelRepository.findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                ParcelStatus.STORED, NOW
        )).thenReturn(List.of(parcel), List.of());
        when(pickupCodeRepository.findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                PickupCodeStatus.ACTIVE, NOW
        )).thenReturn(List.of(code), List.of());

        ExpirationResult first = service.processExpired();
        ExpirationResult second = service.processExpired();

        assertThat(first.expiredParcels()).isOne();
        assertThat(first.expiredPickupCodes()).isOne();
        assertThat(second.expiredParcels()).isZero();
        assertThat(second.expiredPickupCodes()).isZero();
        verify(parcel).expire();
        verify(code).markExpired();
    }
}
