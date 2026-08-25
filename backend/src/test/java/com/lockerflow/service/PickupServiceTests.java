package com.lockerflow.service;

import com.lockerflow.dto.request.PickupParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupServiceTests {

    private static final long PARCEL_ID = 10L;
    private static final long CUSTOMER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PickupCodeRepository pickupCodeRepository;

    @Mock
    private LockerCellRepository cellRepository;

    @Mock
    private PickupCodeHasher pickupCodeHasher;

    private PickupService service;

    @BeforeEach
    void setUp() {
        service = new PickupService(
                parcelRepository,
                userRepository,
                pickupCodeRepository,
                cellRepository,
                pickupCodeHasher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void pickupAtomicallyMutatesCodeParcelAndCellThenFlushesAll() {
        Fixture fixture = happyFixture(NOW.plusSeconds(3600), NOW.plusSeconds(3600));
        when(pickupCodeHasher.matches("004271", "bcrypt-hash")).thenReturn(true);

        ParcelResponse response = service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271"));

        assertThat(response.status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(response.pickedUpAt()).isEqualTo(NOW);
        assertThat(response.lockerCellCode()).isEqualTo("A01");
        assertThat(fixture.parcel().getLockerCell()).isSameAs(fixture.cell());
        assertThat(fixture.parcel().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(fixture.parcel().getPickedUpAt()).isEqualTo(NOW);
        assertThat(fixture.cell().getStatus()).isEqualTo(LockerCellStatus.AVAILABLE);
        assertThat(fixture.code().getStatus()).isEqualTo(PickupCodeStatus.USED);
        assertThat(fixture.code().getUsedAt()).isEqualTo(NOW);

        InOrder flushOrder = inOrder(pickupCodeRepository, parcelRepository, cellRepository);
        flushOrder.verify(pickupCodeRepository).flush();
        flushOrder.verify(parcelRepository).flush();
        flushOrder.verify(cellRepository).flush();
    }

    @Test
    void reportsMissingParcel() {
        when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Parcel 10 was not found");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void reportsMissingCustomer() {
        Fixture fixture = happyFixture();
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer 1 was not found");
        assertUnchanged(fixture);
    }

    @Test
    void rejectsCustomerWithWrongRole() {
        Fixture fixture = happyFixture();
        when(fixture.customer().getRole()).thenReturn(Role.ADMIN);

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer 1 must have role CUSTOMER");
        assertUnchanged(fixture);
    }

    @Test
    void rejectsDisabledCustomer() {
        Fixture fixture = happyFixture();
        when(fixture.customer().getStatus()).thenReturn(UserStatus.DISABLED);

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer 1 is not active");
        assertUnchanged(fixture);
    }

    @Test
    void rejectsWrongOwner() {
        Fixture fixture = happyFixture();
        User anotherCustomer = user(2L, Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(anotherCustomer));

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, 2L, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Customer does not own this parcel");
        assertUnchanged(fixture);
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatus.class, names = {
            "CREATED", "PICKED_UP", "ABNORMAL", "CANCELLED"
    })
    void rejectsEveryDisallowedParcelStatus(ParcelStatus status) {
        User customer = user(CUSTOMER_ID, Role.CUSTOMER, UserStatus.ACTIVE);
        Parcel parcel = mock(Parcel.class);
        when(parcel.getCustomer()).thenReturn(customer);
        when(parcel.getStatus()).thenReturn(status);
        when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Parcel is not available for pickup");
        verify(parcel, never()).pickUp(any());
        verify(pickupCodeRepository, never()).flush();
    }

    @Test
    void normalizedExpiredParcelUsesGenericExpiredCodeMessage() {
        User customer = user(CUSTOMER_ID, Role.CUSTOMER, UserStatus.ACTIVE);
        Parcel parcel = mock(Parcel.class);
        when(parcel.getCustomer()).thenReturn(customer);
        when(parcel.getStatus()).thenReturn(ParcelStatus.EXPIRED);
        when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invalid or expired pickup code");
        verify(parcel, never()).pickUp(any());
        verify(pickupCodeRepository, never()).flush();
    }

    @Test
    void rejectsStoredParcelWithoutLockerCell() {
        User customer = user(CUSTOMER_ID, Role.CUSTOMER, UserStatus.ACTIVE);
        Parcel parcel = mock(Parcel.class);
        when(parcel.getCustomer()).thenReturn(customer);
        when(parcel.getStatus()).thenReturn(ParcelStatus.STORED);
        when(parcel.getLockerCell()).thenReturn(null);
        when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Parcel is not in an occupied locker cell");
        verify(parcel, never()).pickUp(any());
    }

    @Test
    void rejectsLockerCellThatIsNotOccupied() {
        Fixture fixture = happyFixture();
        fixture.cell().changeStatus(LockerCellStatus.AVAILABLE);

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Parcel is not in an occupied locker cell");
        assertThat(fixture.parcel().getStatus()).isEqualTo(ParcelStatus.STORED);
        assertThat(fixture.code().getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
    }

    @Test
    void rejectsParcelExpiredBeforeNow() {
        Fixture fixture = happyFixture(NOW.minusSeconds(1), NOW.plusSeconds(3600));

        assertInvalidCodeAndUnchanged(fixture);
    }

    @Test
    void rejectsParcelExpiringExactlyNow() {
        Fixture fixture = happyFixture(NOW, NOW.plusSeconds(3600));

        assertInvalidCodeAndUnchanged(fixture);
    }

    @Test
    void rejectsStoredParcelWithoutExpiry() {
        User customer = user(CUSTOMER_ID, Role.CUSTOMER, UserStatus.ACTIVE);
        LockerCell cell = occupiedCell();
        Parcel parcel = mock(Parcel.class);
        when(parcel.getCustomer()).thenReturn(customer);
        when(parcel.getStatus()).thenReturn(ParcelStatus.STORED);
        when(parcel.getLockerCell()).thenReturn(cell);
        when(parcel.getExpiresAt()).thenReturn(null);
        when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request("004271")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invalid or expired pickup code");
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        verify(parcel, never()).pickUp(any());
    }

    @Test
    void rejectsWhenNoActivePickupCodeExists() {
        Fixture fixture = happyFixture();
        when(pickupCodeRepository.findFirstForUpdateByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
                PARCEL_ID, PickupCodeStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertInvalidCodeAndUnchanged(fixture);
    }

    @Test
    void rejectsPickupCodeExpiredBeforeNow() {
        Fixture fixture = happyFixture(NOW.plusSeconds(3600), NOW.minusSeconds(1));

        assertInvalidCodeAndUnchanged(fixture);
    }

    @Test
    void rejectsPickupCodeExpiringExactlyNow() {
        Fixture fixture = happyFixture(NOW.plusSeconds(3600), NOW);

        assertInvalidCodeAndUnchanged(fixture);
    }

    @Test
    void rejectsWrongPickupCodeWithoutMutation() {
        Fixture fixture = happyFixture();
        when(pickupCodeHasher.matches("999999", "bcrypt-hash")).thenReturn(false);

        assertInvalidCodeAndUnchanged(fixture, "999999");
    }

    @ParameterizedTest
    @EnumSource(value = PickupCodeStatus.class, names = {"USED", "EXPIRED", "REVOKED"})
    void rejectsEveryNonActivePickupCodeStatus(PickupCodeStatus status) {
        Fixture fixture = happyFixture();
        PickupCode inactiveCode = new PickupCode(
                fixture.parcel(), "other-hash", NOW.plusSeconds(3600)
        );
        switch (status) {
            case USED -> inactiveCode.markUsed(NOW.minusSeconds(1));
            case EXPIRED -> inactiveCode.markExpired();
            case REVOKED -> inactiveCode.revoke();
            default -> throw new IllegalArgumentException("Unexpected status " + status);
        }
        when(pickupCodeRepository.findFirstForUpdateByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
                PARCEL_ID, PickupCodeStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertInvalidCodeAndUnchanged(fixture);
        assertThat(inactiveCode.getStatus()).isEqualTo(status);
    }

    private void assertInvalidCodeAndUnchanged(Fixture fixture) {
        assertInvalidCodeAndUnchanged(fixture, "004271");
    }

    private void assertInvalidCodeAndUnchanged(Fixture fixture, String rawCode) {
        assertThatThrownBy(() -> service.pickUp(PARCEL_ID, CUSTOMER_ID, request(rawCode)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Invalid or expired pickup code");
        assertUnchanged(fixture);
        verify(pickupCodeRepository, never()).flush();
        verify(parcelRepository, never()).flush();
        verify(cellRepository, never()).flush();
    }

    private void assertUnchanged(Fixture fixture) {
        assertThat(fixture.parcel().getStatus()).isEqualTo(ParcelStatus.STORED);
        assertThat(fixture.parcel().getPickedUpAt()).isNull();
        assertThat(fixture.cell().getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(fixture.code().getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(fixture.code().getUsedAt()).isNull();
    }

    private Fixture happyFixture() {
        return happyFixture(NOW.plusSeconds(3600), NOW.plusSeconds(3600));
    }

    private Fixture happyFixture(Instant parcelExpiresAt, Instant codeExpiresAt) {
        User customer = user(CUSTOMER_ID, Role.CUSTOMER, UserStatus.ACTIVE);
        User courier = user(2L, Role.COURIER, UserStatus.ACTIVE);
        LockerCell cell = occupiedCell();
        Parcel parcel = new Parcel("PKG-1", customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, parcelExpiresAt.minusSeconds(3600), parcelExpiresAt);
        PickupCode code = new PickupCode(parcel, "bcrypt-hash", codeExpiresAt);

        lenient().when(parcelRepository.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        lenient().when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        lenient().when(pickupCodeRepository.findFirstForUpdateByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
                PARCEL_ID, PickupCodeStatus.ACTIVE
        )).thenReturn(Optional.of(code));
        lenient().when(pickupCodeHasher.matches("004271", "bcrypt-hash")).thenReturn(true);
        return new Fixture(customer, parcel, cell, code);
    }

    private LockerCell occupiedCell() {
        LockerStation station = mock(LockerStation.class);
        lenient().when(station.getId()).thenReturn(3L);
        lenient().when(station.getName()).thenReturn("Station A");
        lenient().when(station.getAddress()).thenReturn("Block A");
        LockerCell cell = new LockerCell(station, "A01", LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        return cell;
    }

    private User user(Long id, Role role, UserStatus status) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getRole()).thenReturn(role);
        lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }

    private PickupParcelRequest request(String code) {
        return new PickupParcelRequest(code);
    }

    private record Fixture(User customer, Parcel parcel, LockerCell cell, PickupCode code) {
    }
}
