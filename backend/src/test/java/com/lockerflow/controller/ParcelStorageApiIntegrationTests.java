package com.lockerflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import com.lockerflow.repository.UserRepository;
import com.lockerflow.service.PickupCodeHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ParcelStorageApiIntegrationTests.FixedClockConfig.class)
class ParcelStorageApiIntegrationTests {

    private static final Instant STORED_AT = Instant.parse("2026-08-22T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private PickupCodeHasher pickupCodeHasher;

    @AfterEach
    void cleanCommittedFixtureData() {
        pickupCodeRepository.deleteAll();
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void storesParcelWithHttp201AndPersistsBothSides() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        LockerCell cell = saveCell(station, "A01", LockerSize.SMALL);

        MvcResult result = mockMvc.perform(
                        storeRequest(" pkg-2026-0001 ", customer, courier, station, LockerSize.SMALL)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcel.trackingNumber").value("PKG-2026-0001"))
                .andExpect(jsonPath("$.parcel.status").value("STORED"))
                .andExpect(jsonPath("$.parcel.lockerCellCode").value("A01"))
                .andExpect(jsonPath("$.parcel.lockerCellSize").value("SMALL"))
                .andExpect(jsonPath("$.parcel.storedAt").value("2026-08-22T10:00:00Z"))
                .andExpect(jsonPath("$.parcel.expiresAt").value("2026-08-24T10:00:00Z"))
                .andExpect(jsonPath("$.pickupCode").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.pickupCodeExpiresAt").value("2026-08-24T10:00:00Z"))
                .andReturn();

        Parcel stored = parcelRepository.findByTrackingNumberIgnoreCase("PKG-2026-0001").orElseThrow();
        PickupCode pickupCode = pickupCodeRepository
                .findByParcelIdOrderByCreatedAtDescIdDesc(stored.getId()).getFirst();
        String rawCode = objectMapper.readTree(result.getResponse().getContentAsString()).get("pickupCode").asText();
        LockerCell occupied = cellRepository.findById(cell.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ParcelStatus.STORED);
        assertThat(stored.getLockerCell().getId()).isEqualTo(cell.getId());
        assertThat(stored.getStoredAt()).isEqualTo(STORED_AT);
        assertThat(stored.getExpiresAt()).isEqualTo(STORED_AT.plusSeconds(48 * 60 * 60));
        assertThat(occupied.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
        assertThat(pickupCode.getCodeHash()).isNotEqualTo(rawCode);
        assertThat(pickupCodeHasher.matches(rawCode, pickupCode.getCodeHash())).isTrue();
        assertThat(pickupCode.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(pickupCode.getExpiresAt()).isEqualTo(stored.getExpiresAt());
    }

    @Test
    void bestFitPrefersSmallCellEvenWhenLargeCodeSortsFirst() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.LARGE);
        saveCell(station, "Z99", LockerSize.SMALL);

        mockMvc.perform(storeRequest("PKG-BEST-FIT", customer, courier, station, LockerSize.SMALL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcel.lockerCellCode").value("Z99"))
                .andExpect(jsonPath("$.parcel.lockerCellSize").value("SMALL"));
    }

    @Test
    void rejectsInvalidRequestWithHttp400() throws Exception {
        mockMvc.perform(post("/api/courier/parcels")
                        .with(courierJwt("2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "trackingNumber", " ",
                                "customerId", 0,
                                "stationId", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.trackingNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.customerId").exists())
                .andExpect(jsonPath("$.fieldErrors.stationId").exists())
                .andExpect(jsonPath("$.fieldErrors.size").exists());
    }

    @Test
    void reportsMissingCustomerWithHttp404() throws Exception {
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.SMALL);

        mockMvc.perform(post("/api/courier/parcels")
                        .with(courierJwt(courier.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "trackingNumber", "PKG-MISSING",
                                "customerId", 999999,
                                "stationId", station.getId(),
                                "size", "SMALL"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer 999999 was not found"));
    }

    @Test
    void rejectsDuplicateTrackingWithHttp409() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.SMALL);
        parcelRepository.saveAndFlush(new Parcel("PKG-DUPLICATE", customer, courier, LockerSize.SMALL));

        mockMvc.perform(storeRequest("pkg-duplicate", customer, courier, station, LockerSize.SMALL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Parcel tracking number PKG-DUPLICATE already exists"));
    }

    @Test
    void rejectsInactiveStationWithHttp409() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.MAINTENANCE);
        saveCell(station, "A01", LockerSize.SMALL);

        mockMvc.perform(storeRequest("PKG-INACTIVE", customer, courier, station, LockerSize.SMALL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Locker station is not available for parcel storage"));
    }

    @Test
    void rejectsWhenNoSuitableCellExistsWithHttp409() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.SMALL);

        mockMvc.perform(storeRequest("PKG-NO-CELL", customer, courier, station, LockerSize.MEDIUM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("No suitable locker cell is available for parcel size MEDIUM"));
    }

    @Test
    void rejectsInvalidCustomerRoleWithHttp409() throws Exception {
        User admin = saveUser("admin", Role.ADMIN, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.SMALL);

        mockMvc.perform(storeRequest("PKG-ROLE", admin, courier, station, LockerSize.SMALL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Customer %d must have role CUSTOMER".formatted(admin.getId())
                ));
    }

    @Test
    void ignoresSpoofedCourierIdAndUsesJwtSubject() throws Exception {
        User customer = saveUser("spoof.customer", Role.CUSTOMER, 31);
        User authenticatedCourier = saveUser("spoof.courier.a", Role.COURIER, 32);
        User spoofedCourier = saveUser("spoof.courier.b", Role.COURIER, 33);
        LockerStation station = saveStation("Spoof Station", LockerStationStatus.ACTIVE);
        saveCell(station, "S01", LockerSize.SMALL);

        mockMvc.perform(post("/api/courier/parcels")
                        .with(courierJwt(authenticatedCourier.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "trackingNumber", "PKG-SPOOF-COURIER",
                                "customerId", customer.getId(),
                                "courierId", spoofedCourier.getId(),
                                "stationId", station.getId(),
                                "size", "SMALL"
                        ))))
                .andExpect(status().isCreated());

        Parcel stored = parcelRepository.findByTrackingNumberIgnoreCase("PKG-SPOOF-COURIER").orElseThrow();
        assertThat(stored.getCourier().getId()).isEqualTo(authenticatedCourier.getId());
        assertThat(stored.getCourier().getId()).isNotEqualTo(spoofedCourier.getId());
    }

    @Test
    void lockerGridReflectsStorageImmediately() throws Exception {
        User customer = saveUser("customer", Role.CUSTOMER, 1);
        User courier = saveUser("courier", Role.COURIER, 2);
        LockerStation station = saveStation("Station A", LockerStationStatus.ACTIVE);
        saveCell(station, "A01", LockerSize.SMALL);
        saveCell(station, "A02", LockerSize.MEDIUM);

        mockMvc.perform(get("/api/stations/{stationId}/grid", station.getId())
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.available").value(2))
                .andExpect(jsonPath("$.summary.occupied").value(0));

        mockMvc.perform(storeRequest("PKG-GRID", customer, courier, station, LockerSize.SMALL))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/stations/{stationId}/grid", station.getId())
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.available").value(1))
                .andExpect(jsonPath("$.summary.occupied").value(1))
                .andExpect(jsonPath("$.cells[0].cellCode").value("A01"))
                .andExpect(jsonPath("$.cells[0].status").value("OCCUPIED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder storeRequest(
            String trackingNumber,
            User customer,
            User courier,
            LockerStation station,
            LockerSize size
    ) throws Exception {
        return post("/api/courier/parcels")
                .with(courierJwt(courier.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "trackingNumber", trackingNumber,
                        "customerId", customer.getId(),
                        "stationId", station.getId(),
                        "size", size.name()
                )));
    }

    private User saveUser(String username, Role role, int suffix) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                String.format("139%08d", suffix),
                "hashed",
                role
        ));
    }

    private LockerStation saveStation(String name, LockerStationStatus status) {
        LockerStation station = new LockerStation(name, name + " Address");
        station.changeStatus(status);
        return stationRepository.saveAndFlush(station);
    }

    private LockerCell saveCell(LockerStation station, String code, LockerSize size) {
        return cellRepository.saveAndFlush(new LockerCell(station, code, size));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private RequestPostProcessor courierJwt(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"));
    }

    private RequestPostProcessor authenticatedJwt() {
        return jwt().jwt(token -> token.subject("1"));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(STORED_AT, ZoneOffset.UTC);
        }
    }
}
