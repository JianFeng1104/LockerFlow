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
class ParcelStorageRollbackIntegrationTests {

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ParcelRepository parcelRepository;

    @AfterEach
    void cleanCommittedFixtureData() {
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rollsBackCellOccupancyWhenParcelSaveFails() {
        User customer = userRepository.saveAndFlush(new User(
                "rollback.customer", "rollback.customer@example.com", "13700000001", "hashed", Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                "rollback.courier", "rollback.courier@example.com", "13700000002", "hashed", Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Rollback Station", "Rollback Address")
        );
        LockerCell cell = cellRepository.saveAndFlush(
                new LockerCell(station, "R01", LockerSize.SMALL)
        );
        when(parcelRepository.save(any())).thenThrow(new DataIntegrityViolationException("forced test failure"));

        CreateParcelRequest request = new CreateParcelRequest(
                "ROLLBACK-1", customer.getId(), station.getId(), LockerSize.SMALL
        );

        assertThatThrownBy(() -> parcelService.storeParcel(request, courier.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        LockerCell reloaded = cellRepository.findById(cell.getId()).orElseThrow();
        Integer parcelCount = jdbcTemplate.queryForObject(
                "select count(*) from parcels where tracking_number = ?",
                Integer.class,
                "ROLLBACK-1"
        );
        assertThat(reloaded.getStatus()).isEqualTo(LockerCellStatus.AVAILABLE);
        assertThat(parcelCount).isZero();
    }
}
