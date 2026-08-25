package com.lockerflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
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
@Import(PickupWorkflowApiIntegrationTests.FixedClockConfig.class)
class PickupWorkflowApiIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");

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
    void storeThenPickupUpdatesParcelCodeAndGrid() throws Exception {
        ApiFixture fixture = fixture(1);
        IssuedParcel issued = store(fixture, "WORKFLOW-1");

        mockMvc.perform(get("/api/stations/{stationId}/grid", fixture.station().getId())
                        .with(customerJwt(fixture.customer().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.occupied").value(1));

        mockMvc.perform(pickupRequest(issued.parcelId(), fixture.customer().getId(), issued.rawCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.pickedUpAt").value("2026-08-23T10:00:00Z"))
                .andExpect(jsonPath("$.lockerCellCode").value("A01"));

        Parcel parcel = parcelRepository.findById(issued.parcelId()).orElseThrow();
        PickupCode code = pickupCodeRepository
                .findByParcelIdOrderByCreatedAtDescIdDesc(issued.parcelId()).getFirst();
        assertThat(parcel.getStatus().name()).isEqualTo("PICKED_UP");
        assertThat(parcel.getLockerCell().getId()).isEqualTo(fixture.cell().getId());
        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.USED);
        assertThat(code.getUsedAt()).isEqualTo(NOW);
        assertThat(cellRepository.findById(fixture.cell().getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.AVAILABLE);

        mockMvc.perform(get("/api/stations/{stationId}/grid", fixture.station().getId())
                        .with(customerJwt(fixture.customer().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.available").value(1))
                .andExpect(jsonPath("$.summary.occupied").value(0))
                .andExpect(jsonPath("$.cells[0].status").value("AVAILABLE"));
    }

    @Test
    void repeatPickupIsRejectedWithoutChangingUsedTimestamp() throws Exception {
        ApiFixture fixture = fixture(2);
        IssuedParcel issued = store(fixture, "WORKFLOW-2");
        mockMvc.perform(pickupRequest(issued.parcelId(), fixture.customer().getId(), issued.rawCode()))
                .andExpect(status().isOk());
        Instant usedAt = pickupCodeRepository
                .findByParcelIdOrderByCreatedAtDescIdDesc(issued.parcelId()).getFirst().getUsedAt();

        mockMvc.perform(pickupRequest(issued.parcelId(), fixture.customer().getId(), issued.rawCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Parcel is not available for pickup"));

        assertThat(pickupCodeRepository.findByParcelIdOrderByCreatedAtDescIdDesc(issued.parcelId())
                .getFirst().getUsedAt()).isEqualTo(usedAt);
    }

    @Test
    void wrongOwnerIsRejectedWithHttp409() throws Exception {
        ApiFixture fixture = fixture(3);
        User another = saveUser("workflow.other.3", Role.CUSTOMER, 103);
        IssuedParcel issued = store(fixture, "WORKFLOW-3");

        mockMvc.perform(pickupRequest(issued.parcelId(), another.getId(), issued.rawCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Customer does not own this parcel"));

        assertStillStored(issued.parcelId(), fixture.cell().getId());
    }

    @Test
    void spoofedCustomerIdCannotOverrideJwtSubject() throws Exception {
        ApiFixture fixture = fixture(9);
        User authenticatedOther = saveUser("workflow.other.9", Role.CUSTOMER, 109);
        IssuedParcel issued = store(fixture, "WORKFLOW-9");

        mockMvc.perform(post("/api/customer/parcels/{parcelId}/pickup", issued.parcelId())
                        .with(customerJwt(authenticatedOther.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "customerId", fixture.customer().getId(),
                                "pickupCode", issued.rawCode()
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Customer does not own this parcel"));

        assertStillStored(issued.parcelId(), fixture.cell().getId());
    }

    @Test
    void wrongCodeReturnsGenericConflictAndDoesNotMutate() throws Exception {
        ApiFixture fixture = fixture(4);
        IssuedParcel issued = store(fixture, "WORKFLOW-4");

        mockMvc.perform(pickupRequest(issued.parcelId(), fixture.customer().getId(), "999999"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid or expired pickup code"));

        assertStillStored(issued.parcelId(), fixture.cell().getId());
        assertThat(pickupCodeRepository.findByParcelIdOrderByCreatedAtDescIdDesc(issued.parcelId())
                .getFirst().getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
    }

    @Test
    void missingParcelAndCustomerReturnHttp404() throws Exception {
        User customer = saveUser("workflow.customer.5", Role.CUSTOMER, 5);

        mockMvc.perform(pickupRequest(999999L, customer.getId(), "004271"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Parcel 999999 was not found"));

        ApiFixture fixture = fixture(6);
        IssuedParcel issued = store(fixture, "WORKFLOW-6");
        mockMvc.perform(pickupRequest(issued.parcelId(), 999999L, issued.rawCode()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer 999999 was not found"));
    }

    @Test
    void invalidPickupDtoReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/customer/parcels/1/pickup")
                        .with(customerJwt(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pickupCode", "12A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupCode").exists());
    }

    @Test
    void codeExpiringExactlyNowIsRejected() throws Exception {
        ApiFixture fixture = fixture(7);
        fixture.cell().changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(fixture.cell());
        Parcel parcel = new Parcel(
                "WORKFLOW-7",
                fixture.customer(),
                fixture.courier(),
                LockerSize.SMALL
        );
        parcel.storeIn(fixture.cell(), NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, pickupCodeHasher.hash("004271"), NOW)
        );

        mockMvc.perform(pickupRequest(parcel.getId(), fixture.customer().getId(), "004271"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid or expired pickup code"));

        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertStillStored(parcel.getId(), fixture.cell().getId());
    }

    @Test
    void parcelExpiringExactlyNowIsRejected() throws Exception {
        ApiFixture fixture = fixture(8);
        fixture.cell().changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(fixture.cell());
        Parcel parcel = new Parcel(
                "WORKFLOW-8",
                fixture.customer(),
                fixture.courier(),
                LockerSize.SMALL
        );
        parcel.storeIn(fixture.cell(), NOW.minusSeconds(3600), NOW);
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, pickupCodeHasher.hash("004271"), NOW.plusSeconds(3600))
        );

        mockMvc.perform(pickupRequest(parcel.getId(), fixture.customer().getId(), "004271"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid or expired pickup code"));

        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertStillStored(parcel.getId(), fixture.cell().getId());
    }

    private void assertStillStored(Long parcelId, Long cellId) {
        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus().name()).isEqualTo("STORED");
        assertThat(cellRepository.findById(cellId).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.OCCUPIED);
    }

    private IssuedParcel store(ApiFixture fixture, String trackingNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/courier/parcels")
                        .with(courierJwt(fixture.courier().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "trackingNumber", trackingNumber,
                                "customerId", fixture.customer().getId(),
                                "stationId", fixture.station().getId(),
                                "size", "SMALL"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupCode").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new IssuedParcel(body.get("parcel").get("id").asLong(), body.get("pickupCode").asText());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pickupRequest(
            Long parcelId,
            Long customerId,
            String code
    ) throws Exception {
        return post("/api/customer/parcels/{parcelId}/pickup", parcelId)
                .with(customerJwt(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("pickupCode", code)));
    }

    private ApiFixture fixture(int suffix) {
        User customer = saveUser("workflow.customer." + suffix, Role.CUSTOMER, suffix);
        User courier = saveUser("workflow.courier." + suffix, Role.COURIER, suffix + 50);
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Workflow Station " + suffix, "Workflow Address " + suffix)
        );
        LockerCell cell = cellRepository.saveAndFlush(
                new LockerCell(station, "A01", LockerSize.SMALL)
        );
        return new ApiFixture(customer, courier, station, cell);
    }

    private User saveUser(String username, Role role, int suffix) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                String.format("134%08d", suffix),
                "hashed",
                role
        ));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private RequestPostProcessor courierJwt(Long subject) {
        return jwt()
                .jwt(token -> token.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"));
    }

    private RequestPostProcessor customerJwt(Long subject) {
        return jwt()
                .jwt(token -> token.subject(subject.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private record ApiFixture(User customer, User courier, LockerStation station, LockerCell cell) {
    }

    private record IssuedParcel(Long parcelId, String rawCode) {
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
