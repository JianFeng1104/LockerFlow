package com.lockerflow.service;

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
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(ExpirationWorkflowIntegrationTests.FixedClockConfig.class)
class ExpirationWorkflowIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Autowired
    private ExpirationService expirationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private PickupCodeRepository pickupCodeRepository;

    @Test
    void normalizesOnlyEligibleRowsAtInclusiveBoundaryAndKeepsLockersOccupied() {
        User customer = saveUser("expiration.customer", Role.CUSTOMER, "13100000001");
        User courier = saveUser("expiration.courier", Role.COURIER, "13100000002");
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Expiration Station", "Expiration Address")
        );

        Fixture beforeCutoff = storedFixture(
                "EXP-PAST", "E01", NOW.minusSeconds(1), customer, courier, station
        );
        Fixture atCutoff = storedFixture(
                "EXP-BOUNDARY", "E02", NOW, customer, courier, station
        );
        Fixture future = storedFixture(
                "EXP-FUTURE", "E03", NOW.plusSeconds(1), customer, courier, station
        );
        Fixture pickedUp = storedFixture(
                "EXP-PICKED", "E04", NOW.minusSeconds(1), customer, courier, station
        );
        pickedUp.parcel().pickUp(NOW.minusSeconds(10));
        pickedUp.cell().changeStatus(LockerCellStatus.AVAILABLE);
        pickedUp.code().markUsed(NOW.minusSeconds(10));
        PickupCode revoked = new PickupCode(pickedUp.parcel(), "revoked-hash", NOW.minusSeconds(1));
        revoked.revoke();
        pickupCodeRepository.save(revoked);
        PickupCode alreadyExpired = new PickupCode(
                pickedUp.parcel(), "already-expired-hash", NOW.minusSeconds(1)
        );
        alreadyExpired.markExpired();
        pickupCodeRepository.saveAndFlush(alreadyExpired);
        Parcel created = parcelRepository.saveAndFlush(
                new Parcel("EXP-CREATED", customer, courier, LockerSize.SMALL)
        );

        List<Parcel> eligibleParcels = parcelRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        ParcelStatus.STORED,
                        NOW
                );
        assertThat(eligibleParcels)
                .extracting(Parcel::getTrackingNumber)
                .containsExactly("EXP-PAST", "EXP-BOUNDARY");
        List<PickupCode> eligibleCodes = pickupCodeRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        PickupCodeStatus.ACTIVE,
                        NOW
                );
        assertThat(eligibleCodes)
                .extracting(PickupCode::getCodeHash)
                .containsExactly("EXP-PAST-hash", "EXP-BOUNDARY-hash");

        ExpirationResult first = expirationService.processExpired();
        ExpirationResult second = expirationService.processExpired();

        assertThat(first.processedAt()).isEqualTo(NOW);
        assertThat(first.expiredParcels()).isEqualTo(2);
        assertThat(first.expiredPickupCodes()).isEqualTo(2);
        assertThat(second.expiredParcels()).isZero();
        assertThat(second.expiredPickupCodes()).isZero();
        assertThat(beforeCutoff.parcel().getStatus()).isEqualTo(ParcelStatus.EXPIRED);
        assertThat(atCutoff.parcel().getStatus()).isEqualTo(ParcelStatus.EXPIRED);
        assertThat(beforeCutoff.code().getStatus()).isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(atCutoff.code().getStatus()).isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(beforeCutoff.cell().getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(atCutoff.cell().getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(future.parcel().getStatus()).isEqualTo(ParcelStatus.STORED);
        assertThat(future.code().getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(future.cell().getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(pickedUp.parcel().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(pickedUp.code().getStatus()).isEqualTo(PickupCodeStatus.USED);
        assertThat(revoked.getStatus()).isEqualTo(PickupCodeStatus.REVOKED);
        assertThat(alreadyExpired.getStatus()).isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(created.getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    private Fixture storedFixture(
            String trackingNumber,
            String cellCode,
            Instant expiresAt,
            User customer,
            User courier,
            LockerStation station
    ) {
        LockerCell cell = new LockerCell(station, cellCode, LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(cell);
        Parcel parcel = new Parcel(trackingNumber, customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, expiresAt.minusSeconds(3600), expiresAt);
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, trackingNumber + "-hash", expiresAt)
        );
        return new Fixture(parcel, code, cell);
    }

    private User saveUser(String username, Role role, String phone) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                phone,
                "not-used-for-login",
                role
        ));
    }

    private record Fixture(Parcel parcel, PickupCode code, LockerCell cell) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
