package com.lockerflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BearerWorkflowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @AfterEach
    void cleanCommittedFixtureData() {
        pickupCodeRepository.deleteAll();
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void realLoginTokensDriveCourierStorageAndCustomerPickup() throws Exception {
        User customer = saveUser("bearer.customer", "customer-password", Role.CUSTOMER, 1);
        User courier = saveUser("bearer.courier", "courier-password", Role.COURIER, 2);
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Bearer Station", "Bearer Address")
        );
        LockerCell cell = cellRepository.saveAndFlush(
                new LockerCell(station, "B01", LockerSize.SMALL)
        );

        String courierToken = login("bearer.courier", "courier-password");
        MvcResult storageResult = mockMvc.perform(post("/api/courier/parcels")
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "trackingNumber", "BEARER-WORKFLOW-1",
                                "customerId", customer.getId(),
                                "stationId", station.getId(),
                                "size", "SMALL"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcel.status").value("STORED"))
                .andExpect(jsonPath("$.pickupCode").isString())
                .andReturn();

        JsonNode storageBody = objectMapper.readTree(storageResult.getResponse().getContentAsString());
        Long parcelId = storageBody.get("parcel").get("id").asLong();
        String pickupCode = storageBody.get("pickupCode").asText();
        Parcel stored = parcelRepository.findById(parcelId).orElseThrow();
        assertThat(stored.getCourier().getId()).isEqualTo(courier.getId());
        assertThat(stored.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(cellRepository.findById(cell.getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.OCCUPIED);

        String customerToken = login("bearer.customer", "customer-password");
        mockMvc.perform(post("/api/customer/parcels/{parcelId}/pickup", parcelId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pickupCode", pickupCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(cellRepository.findById(cell.getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.AVAILABLE);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private User saveUser(String username, String password, Role role, int suffix) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                String.format("135%08d", suffix),
                passwordEncoder.encode(password),
                role
        ));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
