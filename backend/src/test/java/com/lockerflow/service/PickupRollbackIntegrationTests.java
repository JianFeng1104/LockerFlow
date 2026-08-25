package com.lockerflow.service;

import com.lockerflow.dto.request.PickupParcelRequest;
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
class PickupRollbackIntegrationTests {

    @Autowired
    private PickupService pickupService;

    @Autowired
    private PickupCodeHasher pickupCodeHasher;

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
    void rollsBackAllThreeMutationsWhenFlushFailsAfterChanges() {
        User customer = userRepository.saveAndFlush(new User(
                "pickup.rollback.customer",
                "pickup.rollback.customer@example.com",
                "13200000001",
                "hashed",
                Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                "pickup.rollback.courier",
                "pickup.rollback.courier@example.com",
                "13200000002",
                "hashed",
                Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Pickup Rollback Station", "Pickup Rollback Address")
        );
        LockerCell cell = new LockerCell(station, "P01", LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(cell);
        Instant storedAt = Instant.now();
        Parcel parcel = new Parcel("PICKUP-ROLLBACK-1", customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, storedAt, storedAt.plusSeconds(7200));
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, pickupCodeHasher.hash("004271"), parcel.getExpiresAt())
        );
        doThrow(new DataIntegrityViolationException("forced flush failure"))
                .when(pickupCodeRepository).flush();

        assertThatThrownBy(() -> pickupService.pickUp(
                parcel.getId(),
                customer.getId(),
                new PickupParcelRequest("004271")
        )).isInstanceOf(DataIntegrityViolationException.class);

        reset(pickupCodeRepository);
        Parcel reloadedParcel = parcelRepository.findById(parcel.getId()).orElseThrow();
        LockerCell reloadedCell = cellRepository.findById(cell.getId()).orElseThrow();
        PickupCode reloadedCode = pickupCodeRepository.findById(code.getId()).orElseThrow();
        assertThat(reloadedParcel.getStatus()).isEqualTo(ParcelStatus.STORED);
        assertThat(reloadedParcel.getPickedUpAt()).isNull();
        assertThat(reloadedCell.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(reloadedCode.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(reloadedCode.getUsedAt()).isNull();
    }
}
