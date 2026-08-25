package com.lockerflow.controller;

import com.lockerflow.dto.request.CreateLockerCellRequest;
import com.lockerflow.dto.request.UpdateLockerCellStatusRequest;
import com.lockerflow.dto.response.LockerCellResponse;
import com.lockerflow.service.LockerCellService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LockerCellController {

    private final LockerCellService cellService;

    @PostMapping("/admin/stations/{stationId}/cells")
    public ResponseEntity<LockerCellResponse> createCell(
            @PathVariable Long stationId,
            @Valid @RequestBody CreateLockerCellRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cellService.createCell(stationId, request));
    }

    @GetMapping("/stations/{stationId}/cells")
    public List<LockerCellResponse> getCells(@PathVariable Long stationId) {
        return cellService.getCells(stationId);
    }

    @GetMapping("/stations/{stationId}/cells/{cellId}")
    public LockerCellResponse getCell(@PathVariable Long stationId, @PathVariable Long cellId) {
        return cellService.getCell(stationId, cellId);
    }

    @PatchMapping("/admin/stations/{stationId}/cells/{cellId}/status")
    public LockerCellResponse changeCellStatus(
            @PathVariable Long stationId,
            @PathVariable Long cellId,
            @Valid @RequestBody UpdateLockerCellStatusRequest request
    ) {
        return cellService.changeCellStatus(stationId, cellId, request.status());
    }
}

