package com.lockerflow.service;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.repository.PickupCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupCodeServiceTests {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-24T10:00:00Z");

    @Mock
    private PickupCodeGenerator generator;

    @Mock
    private PickupCodeHasher hasher;

    @Mock
    private PickupCodeRepository pickupCodeRepository;

    private PickupCodeService service;

    @BeforeEach
    void setUp() {
        service = new PickupCodeService(generator, hasher, pickupCodeRepository);
    }

    @Test
    void issuesActiveHashedCodeWithParcelExpiry() {
        Parcel parcel = storedParcel(EXPIRES_AT);
        when(generator.generate()).thenReturn("004271");
        when(hasher.hash("004271")).thenReturn("bcrypt-hash");
        when(pickupCodeRepository.saveAndFlush(any(PickupCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PickupCodeService.IssuedPickupCode issued = service.issueFor(parcel);

        ArgumentCaptor<PickupCode> captor = ArgumentCaptor.forClass(PickupCode.class);
        verify(pickupCodeRepository).saveAndFlush(captor.capture());
        PickupCode saved = captor.getValue();
        assertThat(issued.rawCode()).isEqualTo("004271");
        assertThat(issued.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getParcel()).isSameAs(parcel);
        assertThat(saved.getCodeHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getCodeHash()).isNotEqualTo(issued.rawCode());
        assertThat(saved.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    void rejectsNullParcel() {
        assertThatThrownBy(() -> service.issueFor(null))
                .isInstanceOf(ConflictException.class);
        verify(generator, never()).generate();
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatus.class, names = {
            "CREATED", "PICKED_UP", "EXPIRED", "ABNORMAL", "CANCELLED"
    })
    void rejectsEveryNonStoredParcelStatus(ParcelStatus status) {
        Parcel parcel = org.mockito.Mockito.mock(Parcel.class);
        when(parcel.getStatus()).thenReturn(status);

        assertThatThrownBy(() -> service.issueFor(parcel))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Pickup codes can only be issued for stored parcels");
        verify(pickupCodeRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStoredParcelWithoutExpiry() {
        Parcel parcel = org.mockito.Mockito.mock(Parcel.class);
        when(parcel.getStatus()).thenReturn(ParcelStatus.STORED);
        when(parcel.getExpiresAt()).thenReturn(null);

        assertThatThrownBy(() -> service.issueFor(parcel))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Stored parcel must have an expiration time");
        verify(generator, never()).generate();
    }

    @Test
    void propagatesPersistenceFailureSoOuterStorageTransactionCanRollBack() {
        Parcel parcel = storedParcel(EXPIRES_AT);
        when(generator.generate()).thenReturn("004271");
        when(hasher.hash("004271")).thenReturn("bcrypt-hash");
        when(pickupCodeRepository.saveAndFlush(any(PickupCode.class)))
                .thenThrow(new DataIntegrityViolationException("forced failure"));

        assertThatThrownBy(() -> service.issueFor(parcel))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Parcel storedParcel(Instant expiresAt) {
        Parcel parcel = org.mockito.Mockito.mock(Parcel.class);
        when(parcel.getStatus()).thenReturn(ParcelStatus.STORED);
        when(parcel.getExpiresAt()).thenReturn(expiresAt);
        return parcel;
    }
}
