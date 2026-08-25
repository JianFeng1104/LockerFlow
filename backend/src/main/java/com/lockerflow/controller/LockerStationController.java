package com.lockerflow.controller;

import com.lockerflow.dto.request.CreateLockerStationRequest;
import com.lockerflow.dto.request.UpdateLockerStationRequest;
import com.lockerflow.dto.request.UpdateLockerStationStatusRequest;
import com.lockerflow.dto.response.LockerGridResponse;
import com.lockerflow.dto.response.LockerStationResponse;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.service.LockerStationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LockerStationController {

    private final LockerStationService stationService;

    @PostMapping("/admin/stations")
    public ResponseEntity<LockerStationResponse> createStation(
            @Valid @RequestBody CreateLockerStationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationService.createStation(request));
    }

    @GetMapping("/stations")
    public List<LockerStationResponse> getStations(
            @RequestParam(required = false) LockerStationStatus status
    ) {
        return stationService.getStations(status);
    }

    @GetMapping("/stations/{stationId}")
    public LockerStationResponse getStation(@PathVariable Long stationId) {
        return stationService.getStation(stationId);
    }

    @PutMapping("/admin/stations/{stationId}")
    public LockerStationResponse updateStation(
            @PathVariable Long stationId,
            @Valid @RequestBody UpdateLockerStationRequest request
    ) {
        return stationService.updateStation(stationId, request);
    }

    @PatchMapping("/admin/stations/{stationId}/status")
    public LockerStationResponse changeStationStatus(
            @PathVariable Long stationId,
            @Valid @RequestBody UpdateLockerStationStatusRequest request
    ) {
        return stationService.changeStationStatus(stationId, request.status());
    }

    @GetMapping("/stations/{stationId}/grid")
    public LockerGridResponse getGrid(@PathVariable Long stationId) {
        return stationService.getGrid(stationId);
    }
}

