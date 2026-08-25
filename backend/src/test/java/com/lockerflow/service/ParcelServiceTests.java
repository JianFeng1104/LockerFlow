package com.lockerflow.service;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.exception.ResourceNotFoundException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTests {

    private static final Instant STORED_AT = Instant.parse("2026-08-22T10:00:00Z");

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LockerStationRepository stationRepository;

    @Mock
    private LockerCellRepository cellRepository;

    @Mock
    private LockerAllocationService allocationService;

    @Mock
    private PickupCodeService pickupCodeService;

    private ParcelStorageTransactionService parcelStorageTransactionService;

    @BeforeEach
    void setUp() {
        parcelStorageTransactionService = new ParcelStorageTransactionService(
                parcelRepository,
                userRepository,
                stationRepository,
                cellRepository,
                allocationService,
                pickupCodeService,
                Clock.fixed(STORED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void storesSmallParcelInSmallCell() {
        StorageFixture fixture = stubSuccessfulStorage(LockerSize.SMALL, LockerSize.SMALL);

        StoreParcelResponse response = parcelStorageTransactionService.storeParcel(
                request("PKG-1", LockerSize.SMALL),
                2L
        );

        assertThat(response.parcel().status()).isEqualTo(ParcelStatus.STORED);
        assertThat(response.parcel().lockerCellSize()).isEqualTo(LockerSize.SMALL);
        assertThat(response.parcel().storedAt()).isEqualTo(STORED_AT);
        assertThat(response.parcel().expiresAt()).isEqualTo(STORED_AT.plusSeconds(48 * 60 * 60));
        assertThat(response.pickupCode()).isEqualTo("004271");
        assertThat(response.pickupCodeExpiresAt()).isEqualTo(STORED_AT.plusSeconds(48 * 60 * 60));
        assertThat(fixture.cell().getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(fixture.savedParcel().getLockerCell()).isSameAs(fixture.cell());
        assertThat(fixture.savedParcel().getStatus()).isEqualTo(ParcelStatus.STORED);
    }

    @Test
    void storesSmallParcelInMediumFallbackCell() {
        stubSuccessfulStorage(LockerSize.SMALL, LockerSize.MEDIUM);

        StoreParcelResponse response = parcelStorageTransactionService.storeParcel(
                request("PKG-2", LockerSize.SMALL),
                2L
        );

        assertThat(response.parcel().size()).isEqualTo(LockerSize.SMALL);
        assertThat(response.parcel().lockerCellSize()).isEqualTo(LockerSize.MEDIUM);
    }

    @Test
    void storesMediumParcelInMediumCell() {
        stubSuccessfulStorage(LockerSize.MEDIUM, LockerSize.MEDIUM);

        StoreParcelResponse response = parcelStorageTransactionService.storeParcel(
                request("PKG-3", LockerSize.MEDIUM),
                2L
        );

        assertThat(response.parcel().lockerCellSize()).isEqualTo(LockerSize.MEDIUM);
    }

    @Test
    void storesLargeParcelInLargeCell() {
        stubSuccessfulStorage(LockerSize.LARGE, LockerSize.LARGE);

        StoreParcelResponse response = parcelStorageTransactionService.storeParcel(
                request("PKG-4", LockerSize.LARGE),
                2L
        );

        assertThat(response.parcel().lockerCellSize()).isEqualTo(LockerSize.LARGE);
    }

    @Test
    void normalizesTrackingNumberBeforeDuplicateCheckAndStorage() {
        StorageFixture fixture = stubSuccessfulStorage(LockerSize.SMALL, LockerSize.SMALL);

        StoreParcelResponse response = parcelStorageTransactionService.storeParcel(
                request(" abc-123 ", LockerSize.SMALL),
                2L
        );

        assertThat(response.parcel().trackingNumber()).isEqualTo("ABC-123");
        assertThat(fixture.savedParcel().getTrackingNumber()).isEqualTo("ABC-123");
        verify(parcelRepository).existsByTrackingNumberIgnoreCase("ABC-123");
    }

    @Test
    void rejectsDuplicateTrackingNumber() {
        when(parcelRepository.existsByTrackingNumberIgnoreCase("ABC-123")).thenReturn(true);

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request(" abc-123 ", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Parcel tracking number ABC-123 already exists");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingCustomer() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-7", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 1 was not found");
    }

    @Test
    void rejectsCustomerWithWrongRole() {
        User customer = user(1L, Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-8", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer 1 must have role CUSTOMER");
    }

    @Test
    void rejectsDisabledCustomer() {
        User customer = user(1L, Role.CUSTOMER, UserStatus.DISABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-9", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer 1 is not active");
    }

    @Test
    void rejectsMissingCourier() {
        stubCustomer();
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-10", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Courier 2 was not found");
    }

    @Test
    void rejectsCourierWithWrongRole() {
        stubCustomer();
        User courier = user(2L, Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-11", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Courier 2 must have role COURIER");
    }

    @Test
    void rejectsDisabledCourier() {
        stubCustomer();
        User courier = user(2L, Role.COURIER, UserStatus.DISABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-12", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Courier 2 is not active");
    }

    @Test
    void rejectsMissingStation() {
        stubUsers();
        when(stationRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-13", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Locker station 3 was not found");
    }

    @Test
    void rejectsMaintenanceStation() {
        stubUsers();
        LockerStation station = station(LockerStationStatus.MAINTENANCE);
        when(stationRepository.findById(3L)).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-14", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Locker station is not available for parcel storage");
    }

    @Test
    void rejectsDisabledStation() {
        stubUsers();
        LockerStation station = station(LockerStationStatus.DISABLED);
        when(stationRepository.findById(3L)).thenReturn(Optional.of(station));

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-15", LockerSize.SMALL),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Locker station is not available for parcel storage");
    }

    @Test
    void rejectsWhenNoSuitableCellExists() {
        stubUsers();
        LockerStation station = station(LockerStationStatus.ACTIVE);
        when(stationRepository.findById(3L)).thenReturn(Optional.of(station));
        when(allocationService.allocate(station, LockerSize.MEDIUM)).thenThrow(
                new ConflictException("No suitable locker cell is available for parcel size MEDIUM")
        );

        assertThatThrownBy(() -> parcelStorageTransactionService.storeParcel(
                request("PKG-16", LockerSize.MEDIUM),
                2L
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No suitable locker cell is available for parcel size MEDIUM");
        verify(parcelRepository, never()).save(any());
    }

    private StorageFixture stubSuccessfulStorage(LockerSize parcelSize, LockerSize cellSize) {
        stubUsers();
        LockerStation station = station(LockerStationStatus.ACTIVE);
        LockerCell cell = new LockerCell(station, "A01", cellSize);
        when(stationRepository.findById(3L)).thenReturn(Optional.of(station));
        when(allocationService.allocate(station, parcelSize)).thenReturn(cell);

        Parcel[] savedParcel = new Parcel[1];
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> {
            savedParcel[0] = invocation.getArgument(0);
            return savedParcel[0];
        });
        when(pickupCodeService.issueFor(any(Parcel.class))).thenAnswer(invocation -> {
            Parcel parcel = invocation.getArgument(0);
            return new PickupCodeService.IssuedPickupCode("004271", parcel.getExpiresAt());
        });
        return new StorageFixture(cell, savedParcel);
    }

    private void stubUsers() {
        stubCustomer();
        User courier = user(2L, Role.COURIER, UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(courier));
    }

    private void stubCustomer() {
        User customer = user(1L, Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
    }

    private User user(Long id, Role role, UserStatus status) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }

    private LockerStation station(LockerStationStatus status) {
        LockerStation station = mock(LockerStation.class);
        lenient().when(station.getId()).thenReturn(3L);
        lenient().when(station.getName()).thenReturn("Station A");
        lenient().when(station.getAddress()).thenReturn("Block A");
        when(station.getStatus()).thenReturn(status);
        return station;
    }

    private CreateParcelRequest request(String trackingNumber, LockerSize size) {
        return new CreateParcelRequest(trackingNumber, 1L, 3L, size);
    }

    private record StorageFixture(LockerCell cell, Parcel[] savedParcelHolder) {
        private Parcel savedParcel() {
            return savedParcelHolder[0];
        }
    }
}
