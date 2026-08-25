package com.lockerflow.service;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PickupCodeIssuanceRollbackIntegrationTests {

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PickupCodeRepository pickupCodeRepository;

    @AfterEach
    void cleanCommittedFixtureData() {
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rollsBackParcelAndCellWhenPickupCodePersistenceFails() {
        User customer = userRepository.saveAndFlush(new User(
                "code.rollback.customer",
                "code.rollback.customer@example.com",
                "13300000001",
                "hashed",
                Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                "code.rollback.courier",
                "code.rollback.courier@example.com",
                "13300000002",
                "hashed",
                Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Code Rollback Station", "Code Rollback Address")
        );
        LockerCell cell = cellRepository.saveAndFlush(
                new LockerCell(station, "C01", LockerSize.SMALL)
        );
        when(pickupCodeRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("forced pickup code failure"));

        CreateParcelRequest request = new CreateParcelRequest(
                "CODE-ROLLBACK-1",
                customer.getId(),
                station.getId(),
                LockerSize.SMALL
        );

        assertThatThrownBy(() -> parcelService.storeParcel(request, courier.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(cellRepository.findById(cell.getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.AVAILABLE);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from parcels where tracking_number = ?",
                Integer.class,
                "CODE-ROLLBACK-1"
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pickup_codes",
                Integer.class
        )).isZero();
    }
}
