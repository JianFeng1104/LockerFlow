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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class ExpirationRollbackIntegrationTests {

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

    @MockitoSpyBean
    private PickupCodeRepository pickupCodeRepository;

    @AfterEach
    void cleanCommittedFixtureData() {
        reset(pickupCodeRepository);
        pickupCodeRepository.deleteAll();
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rollsBackParcelAndCodeNormalizationWhenSecondFlushFails() {
        User customer = userRepository.saveAndFlush(new User(
                "expiration.rollback.customer",
                "expiration.rollback.customer@example.com",
                "12900000001",
                "not-used-for-login",
                Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                "expiration.rollback.courier",
                "expiration.rollback.courier@example.com",
                "12900000002",
                "not-used-for-login",
                Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Expiration Rollback Station", "Expiration Rollback Address")
        );
        LockerCell cell = new LockerCell(station, "R01", LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(cell);
        Instant expiredAt = Instant.now().minusSeconds(60);
        Parcel parcel = new Parcel("EXPIRATION-ROLLBACK", customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, expiredAt.minusSeconds(3600), expiredAt);
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, "rollback-hash", expiredAt)
        );
        doThrow(new DataIntegrityViolationException("forced expiration flush failure"))
                .when(pickupCodeRepository).flush();

        assertThatThrownBy(expirationService::processExpired)
                .isInstanceOf(DataIntegrityViolationException.class);

        reset(pickupCodeRepository);
        assertThat(parcelRepository.findById(parcel.getId()).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.STORED);
        assertThat(pickupCodeRepository.findById(code.getId()).orElseThrow().getStatus())
                .isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(cellRepository.findById(cell.getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.OCCUPIED);
    }
}
