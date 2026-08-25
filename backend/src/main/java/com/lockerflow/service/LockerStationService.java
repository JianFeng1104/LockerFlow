package com.lockerflow.service;

import com.lockerflow.dto.request.CreateLockerStationRequest;
import com.lockerflow.dto.request.UpdateLockerStationRequest;
import com.lockerflow.dto.response.LockerGridResponse;
import com.lockerflow.dto.response.LockerStationResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.exception.BadRequestException;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.exception.ResourceNotFoundException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LockerStationService {

    private static final Set<ParcelStatus> BLOCKING_PARCEL_STATUSES = Set.of(
            ParcelStatus.CREATED,
            ParcelStatus.STORED,
            ParcelStatus.EXPIRED,
            ParcelStatus.ABNORMAL
    );

    private final LockerStationRepository stationRepository;
    private final LockerCellRepository cellRepository;
    private final ParcelRepository parcelRepository;

    @Transactional
    public LockerStationResponse createStation(CreateLockerStationRequest request) {
        LockerStation station = stationRepository.save(new LockerStation(request.name(), request.address()));
        return LockerStationResponse.from(station, List.of());
    }

    @Transactional(readOnly = true)
    public LockerStationResponse getStation(Long stationId) {
        LockerStation station = requireStation(stationId);
        List<LockerCell> cells = cellRepository.findByStationIdOrderByCellCodeAsc(stationId);
        return LockerStationResponse.from(station, cells);
    }

    @Transactional(readOnly = true)
    public List<LockerStationResponse> getStations(LockerStationStatus status) {
        List<LockerStation> stations = status == null
                ? stationRepository.findAllByOrderByNameAsc()
                : stationRepository.findByStatusOrderByNameAsc(status);
        if (stations.isEmpty()) {
            return List.of();
        }

        List<Long> stationIds = stations.stream().map(LockerStation::getId).toList();
        Map<Long, List<LockerCell>> cellsByStation = cellRepository
                .findByStationIdInOrderByStationIdAscCellCodeAsc(stationIds)
                .stream()
                .collect(Collectors.groupingBy(cell -> cell.getStation().getId()));

        return stations.stream()
                .map(station -> LockerStationResponse.from(
                        station,
                        cellsByStation.getOrDefault(station.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public LockerStationResponse updateStation(Long stationId, UpdateLockerStationRequest request) {
        LockerStation station = requireStation(stationId);
        station.updateDetails(request.name(), request.address());
        stationRepository.flush();
        List<LockerCell> cells = cellRepository.findByStationIdOrderByCellCodeAsc(stationId);
        return LockerStationResponse.from(station, cells);
    }

    @Transactional
    public LockerStationResponse changeStationStatus(Long stationId, LockerStationStatus targetStatus) {
        if (targetStatus == null) {
            throw new BadRequestException("Station status must not be null");
        }

        LockerStation station = requireStation(stationId);
        LockerStationStatus currentStatus = station.getStatus();
        if (currentStatus == targetStatus) {
            return responseWithCells(station);
        }

        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new ConflictException(
                    "Station status transition from %s to %s is not allowed"
                            .formatted(currentStatus, targetStatus)
            );
        }

        if ((targetStatus == LockerStationStatus.MAINTENANCE
                || targetStatus == LockerStationStatus.DISABLED)
                && hasBlockingOccupancy(stationId)) {
            String message = targetStatus == LockerStationStatus.MAINTENANCE
                    ? "Station cannot enter maintenance while occupied cells exist"
                    : "Station cannot be disabled while occupied cells exist";
            throw new ConflictException(message);
        }

        station.changeStatus(targetStatus);
        stationRepository.flush();
        return responseWithCells(station);
    }

    @Transactional(readOnly = true)
    public LockerGridResponse getGrid(Long stationId) {
        LockerStation station = requireStation(stationId);
        List<LockerCell> cells = cellRepository.findByStationIdOrderByCellCodeAsc(stationId);
        return LockerGridResponse.from(station, cells);
    }

    private LockerStation requireStation(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Locker station %d was not found".formatted(stationId)
                ));
    }

    private LockerStationResponse responseWithCells(LockerStation station) {
        List<LockerCell> cells = cellRepository.findByStationIdOrderByCellCodeAsc(station.getId());
        return LockerStationResponse.from(station, cells);
    }

    private boolean hasBlockingOccupancy(Long stationId) {
        return cellRepository.existsByStationIdAndStatus(stationId, LockerCellStatus.OCCUPIED)
                || parcelRepository.existsByLockerCellStationIdAndStatusIn(
                        stationId,
                        BLOCKING_PARCEL_STATUSES
                );
    }

    private boolean isAllowedTransition(
            LockerStationStatus currentStatus,
            LockerStationStatus targetStatus
    ) {
        return switch (currentStatus) {
            case ACTIVE -> targetStatus == LockerStationStatus.MAINTENANCE
                    || targetStatus == LockerStationStatus.DISABLED;
            case MAINTENANCE, DISABLED -> targetStatus == LockerStationStatus.ACTIVE;
        };
    }
}
