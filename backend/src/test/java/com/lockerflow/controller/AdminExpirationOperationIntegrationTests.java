package com.lockerflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lockerflow.scheduling.ExpirationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AdminExpirationOperationIntegrationTests.FixedClockConfig.class)
class AdminExpirationOperationIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

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
    void adminRunIsIdempotentAndKeepsExpiredParcelPhysicallyBlockingLocker() throws Exception {
        User customer = userRepository.saveAndFlush(new User(
                "operation.customer",
                "operation.customer@example.com",
                "13000000001",
                "not-used-for-login",
                Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                "operation.courier",
                "operation.courier@example.com",
                "13000000002",
                "not-used-for-login",
                Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Operation Station", "Operation Address")
        );
        LockerCell cell = new LockerCell(station, "O01", LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(cell);
        Parcel parcel = new Parcel("OPERATION-EXPIRED", customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, NOW.minusSeconds(7200), NOW);
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, "bcrypt-hash-not-exposed", NOW)
        );

        mockMvc.perform(post("/api/admin/operations/expiration/run").with(role(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedAt").value("2026-08-23T12:00:00Z"))
                .andExpect(jsonPath("$.expiredParcels").value(1))
                .andExpect(jsonPath("$.expiredPickupCodes").value(1));
        mockMvc.perform(post("/api/admin/operations/expiration/run").with(role(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredParcels").value(0))
                .andExpect(jsonPath("$.expiredPickupCodes").value(0));

        assertThat(parcelRepository.findById(parcel.getId()).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.EXPIRED);
        assertThat(pickupCodeRepository.findById(code.getId()).orElseThrow().getStatus())
                .isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(cellRepository.findById(cell.getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.OCCUPIED);

        mockMvc.perform(get("/api/stations/{stationId}/grid", station.getId()).with(role(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.occupied").value(1))
                .andExpect(jsonPath("$.summary.available").value(0))
                .andExpect(jsonPath("$.cells[0].status").value("OCCUPIED"));

        assertStationTransitionBlocked(station.getId(), "MAINTENANCE");
        assertStationTransitionBlocked(station.getId(), "DISABLED");
        assertCellTransitionBlocked(station.getId(), cell.getId(), "MAINTENANCE");
        assertCellTransitionBlocked(station.getId(), cell.getId(), "AVAILABLE");

        mockMvc.perform(post("/api/customer/parcels/{parcelId}/pickup", parcel.getId())
                        .with(role(Role.CUSTOMER, customer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pickupCode", "004271"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid or expired pickup code"));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.EXPIRED);
        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
    }

    @Test
    void testProfileDisablesBackgroundSchedulerBean() {
        assertThat(applicationContext.getBeansOfType(ExpirationScheduler.class)).isEmpty();
    }

    private void assertStationTransitionBlocked(Long stationId, String statusValue) throws Exception {
        mockMvc.perform(patch("/api/admin/stations/{stationId}/status", stationId)
                        .with(role(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", statusValue))))
                .andExpect(status().isConflict());
    }

    private void assertCellTransitionBlocked(Long stationId, Long cellId, String statusValue) throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/stations/{stationId}/cells/{cellId}/status",
                        stationId,
                        cellId
                )
                        .with(role(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", statusValue))))
                .andExpect(status().isConflict());
    }

    private RequestPostProcessor role(Role role) {
        return role(role, 1L);
    }

    private RequestPostProcessor role(Role role, Long subject) {
        return jwt()
                .jwt(token -> token.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
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
