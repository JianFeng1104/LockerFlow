package com.lockerflow.entity;

import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CoreDomainModelTests {

    @Test
    void newUserIsActiveAndNormalizesEmail() {
        User user = new User("customer1", "Customer@Example.com", "13800000001", "hashed", Role.CUSTOMER);

        assertThat(user.getEmail()).isEqualTo("customer@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void userRejectsBlankIdentityFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User(" ", "customer@example.com", "13800000001", "hashed", Role.CUSTOMER))
                .withMessageContaining("username");
    }

    @Test
    void stationStartsActive() {
        LockerStation station = new LockerStation("Station A", "Block A Lobby");

        assertThat(station.getStatus()).isEqualTo(LockerStationStatus.ACTIVE);
    }

    @Test
    void cellStartsAvailableAndNormalizesCode() {
        LockerStation station = new LockerStation("Station A", "Block A Lobby");
        LockerCell cell = new LockerCell(station, " a01 ", LockerSize.SMALL);

        assertThat(cell.getCellCode()).isEqualTo("A01");
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.AVAILABLE);
    }

    @Test
    void parcelStartsCreatedWithoutLocker() {
        User customer = user("customer", Role.CUSTOMER);
        User courier = user("courier", Role.COURIER);
        Parcel parcel = new Parcel(" sf-1001 ", customer, courier, LockerSize.MEDIUM);

        assertThat(parcel.getTrackingNumber()).isEqualTo("SF-1001");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getLockerCell()).isNull();
    }

    @Test
    void storingParcelRequiresExpiryAfterStorageTime() {
        User customer = user("customer", Role.CUSTOMER);
        User courier = user("courier", Role.COURIER);
        LockerStation station = new LockerStation("Station A", "Block A Lobby");
        LockerCell cell = new LockerCell(station, "A01", LockerSize.SMALL);
        Parcel parcel = new Parcel("SF-1002", customer, courier, LockerSize.SMALL);
        Instant storedAt = Instant.parse("2026-08-22T00:00:00Z");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> parcel.storeIn(cell, storedAt, storedAt))
                .withMessageContaining("after");
    }

    @Test
    void storedParcelExpiresWithoutChangingPhysicalOrHistoricalFields() {
        User customer = user("customer", Role.CUSTOMER);
        User courier = user("courier", Role.COURIER);
        LockerCell cell = new LockerCell(
                new LockerStation("Station A", "Block A Lobby"),
                "A01",
                LockerSize.SMALL
        );
        Parcel parcel = new Parcel("SF-1003", customer, courier, LockerSize.SMALL);
        Instant storedAt = Instant.parse("2026-08-22T00:00:00Z");
        Instant expiresAt = storedAt.plusSeconds(3600);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        parcel.storeIn(cell, storedAt, expiresAt);

        parcel.expire();

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.EXPIRED);
        assertThat(parcel.getLockerCell()).isSameAs(cell);
        assertThat(parcel.getStoredAt()).isEqualTo(storedAt);
        assertThat(parcel.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(parcel.getPickedUpAt()).isNull();
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
    }

    @Test
    void onlyStoredParcelCanExpire() {
        Parcel created = new Parcel(
                "SF-1004",
                user("customer", Role.CUSTOMER),
                user("courier", Role.COURIER),
                LockerSize.SMALL
        );

        assertThatIllegalStateException()
                .isThrownBy(created::expire)
                .withMessage("Only a stored parcel can expire");
    }

    private User user(String username, Role role) {
        return new User(
                username,
                username + "@example.com",
                role == Role.CUSTOMER ? "13800000001" : "13800000002",
                "hashed",
                role
        );
    }
}
