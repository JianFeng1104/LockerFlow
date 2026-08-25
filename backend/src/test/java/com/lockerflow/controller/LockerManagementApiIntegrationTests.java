package com.lockerflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LockerManagementApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Test
    void createsStationWithHttp201() throws Exception {
        mockMvc.perform(post("/api/admin/stations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "KL Sentral Locker",
                                "address", "KL Sentral, Kuala Lumpur"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("KL Sentral Locker"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalCells").value(0));
    }

    @Test
    void getsStationWithSummary() throws Exception {
        LockerStation station = saveStation("Station A");
        cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));

        mockMvc.perform(get("/api/stations/{stationId}", station.getId())
                        .with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(station.getId()))
                .andExpect(jsonPath("$.totalCells").value(1))
                .andExpect(jsonPath("$.availableCells").value(1));
    }

    @Test
    void reportsMissingStationWithHttp404() throws Exception {
        mockMvc.perform(get("/api/stations/999999")
                        .with(readJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Locker station 999999 was not found"))
                .andExpect(jsonPath("$.path").value("/api/stations/999999"));
    }

    @Test
    void rejectsInvalidStationRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/stations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", " ", "address", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.address").value("address must not be blank"));
    }

    @Test
    void filtersStationsByStatus() throws Exception {
        saveStation("Station Active");
        LockerStation maintenance = new LockerStation("Station Maintenance", "Station Maintenance Address");
        maintenance.changeStatus(LockerStationStatus.MAINTENANCE);
        stationRepository.saveAndFlush(maintenance);

        mockMvc.perform(get("/api/stations").queryParam("status", "MAINTENANCE")
                        .with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Station Maintenance"));
    }

    @Test
    void rejectsDuplicateCellWithHttp409() throws Exception {
        LockerStation station = saveStation("Station A");
        cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));

        mockMvc.perform(post("/api/admin/stations/{stationId}/cells", station.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cellCode", "a01", "size", "MEDIUM"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Locker cell A01 already exists in this station"));
    }

    @Test
    void createsCellWithHttp201() throws Exception {
        LockerStation station = saveStation("Station A");

        mockMvc.perform(post("/api/admin/stations/{stationId}/cells", station.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cellCode", " a01 ", "size", "SMALL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationId").value(station.getId()))
                .andExpect(jsonPath("$.cellCode").value("A01"))
                .andExpect(jsonPath("$.size").value("SMALL"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void changesCellStatusAndReturnsIncrementedVersion() throws Exception {
        LockerStation station = saveStation("Station A");
        LockerCell cell = cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));
        long initialVersion = cell.getVersion();

        mockMvc.perform(patch("/api/admin/stations/{stationId}/cells/{cellId}/status", station.getId(), cell.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "MAINTENANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.version").value(initialVersion + 1));
    }

    @Test
    void rejectsManualOccupiedStatusWithHttp409() throws Exception {
        LockerStation station = saveStation("Station A");
        LockerCell cell = cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));

        mockMvc.perform(patch("/api/admin/stations/{stationId}/cells/{cellId}/status", station.getId(), cell.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "OCCUPIED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "OCCUPIED status is managed by parcel storage and cannot be set manually"
                ));
    }

    @Test
    void returnsCompleteLockerGridAndCorrectSummary() throws Exception {
        LockerStation station = saveStation("Station A");
        LockerCell available = new LockerCell(station, "A01", LockerSize.SMALL);
        LockerCell occupied = new LockerCell(station, "A02", LockerSize.MEDIUM);
        occupied.changeStatus(LockerCellStatus.OCCUPIED);
        LockerCell maintenance = new LockerCell(station, "A03", LockerSize.LARGE);
        maintenance.changeStatus(LockerCellStatus.MAINTENANCE);
        LockerCell disabled = new LockerCell(station, "A04", LockerSize.SMALL);
        disabled.changeStatus(LockerCellStatus.DISABLED);
        cellRepository.saveAllAndFlush(java.util.List.of(available, occupied, maintenance, disabled));

        mockMvc.perform(get("/api/stations/{stationId}/grid", station.getId())
                        .with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station.id").value(station.getId()))
                .andExpect(jsonPath("$.station.status").value("ACTIVE"))
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.available").value(1))
                .andExpect(jsonPath("$.summary.occupied").value(1))
                .andExpect(jsonPath("$.summary.maintenance").value(1))
                .andExpect(jsonPath("$.summary.disabled").value(1))
                .andExpect(jsonPath("$.cells.length()").value(4))
                .andExpect(jsonPath("$.cells[0].cellCode").value("A01"))
                .andExpect(jsonPath("$.cells[3].cellCode").value("A04"));
    }

    @Test
    void hidesCellThatBelongsToAnotherStation() throws Exception {
        LockerStation first = saveStation("Station A");
        LockerStation second = saveStation("Station B");
        LockerCell cell = cellRepository.saveAndFlush(new LockerCell(second, "B01", LockerSize.SMALL));

        mockMvc.perform(get("/api/stations/{stationId}/cells/{cellId}", first.getId(), cell.getId())
                        .with(readJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Locker cell %d was not found in station %d".formatted(cell.getId(), first.getId())
                ));
    }

    @Test
    void rejectsInvalidStatusQueryValue() throws Exception {
        mockMvc.perform(get("/api/stations").queryParam("status", "BROKEN")
                        .with(readJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'BROKEN' for parameter 'status'"));
    }

    private LockerStation saveStation(String name) {
        return stationRepository.saveAndFlush(new LockerStation(name, name + " Address"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token.subject("1"));
    }
}
