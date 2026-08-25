package com.lockerflow.service;

import com.lockerflow.dto.request.CreateLockerCellRequest;
import com.lockerflow.dto.response.LockerCellResponse;
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
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LockerCellService {

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
    public LockerCellResponse createCell(Long stationId, CreateLockerCellRequest request) {
        LockerStation station = requireStation(stationId);
        if (station.getStatus() == LockerStationStatus.DISABLED) {
            throw new ConflictException("Cannot add locker cells to a disabled station");
        }

        String normalizedCode = normalizeCellCode(request.cellCode());
        if (cellRepository.existsByStationIdAndCellCodeIgnoreCase(stationId, normalizedCode)) {
            throw new ConflictException(
                    "Locker cell %s already exists in this station".formatted(normalizedCode)
            );
        }

        LockerCell cell = cellRepository.save(new LockerCell(station, normalizedCode, request.size()));
        return LockerCellResponse.from(cell);
    }

    @Transactional(readOnly = true)
    public List<LockerCellResponse> getCells(Long stationId) {
        requireStation(stationId);
        return cellRepository.findByStationIdOrderByCellCodeAsc(stationId)
                .stream()
                .map(LockerCellResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LockerCellResponse getCell(Long stationId, Long cellId) {
        return LockerCellResponse.from(requireCellInStation(stationId, cellId));
    }

    @Transactional
    public LockerCellResponse changeCellStatus(
            Long stationId,
            Long cellId,
            LockerCellStatus targetStatus
    ) {
        if (targetStatus == null) {
            throw new BadRequestException("Locker cell status must not be null");
        }

        LockerCell cell = requireCellInStation(stationId, cellId);
        LockerCellStatus currentStatus = cell.getStatus();

        if (targetStatus == LockerCellStatus.OCCUPIED) {
            throw new ConflictException("OCCUPIED status is managed by parcel storage and cannot be set manually");
        }
        if (currentStatus == LockerCellStatus.OCCUPIED) {
            throw new ConflictException("An occupied locker cell cannot be changed through the admin status API");
        }
        if (currentStatus == targetStatus) {
            return LockerCellResponse.from(cell);
        }
        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new ConflictException(
                    "Locker cell status transition from %s to %s is not allowed"
                            .formatted(currentStatus, targetStatus)
            );
        }
        if (parcelRepository.existsByLockerCellIdAndStatusIn(cellId, BLOCKING_PARCEL_STATUSES)) {
            throw new ConflictException(
                    "Locker cell %s has an active parcel and cannot be changed manually"
                            .formatted(cell.getCellCode())
            );
        }

        cell.changeStatus(targetStatus);
        cellRepository.flush();
        return LockerCellResponse.from(cell);
    }

    private LockerStation requireStation(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Locker station %d was not found".formatted(stationId)
                ));
    }

    private LockerCell requireCellInStation(Long stationId, Long cellId) {
        return cellRepository.findByIdAndStationId(cellId, stationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Locker cell %d was not found in station %d".formatted(cellId, stationId)
                ));
    }

    private String normalizeCellCode(String cellCode) {
        if (cellCode == null || cellCode.isBlank()) {
            throw new IllegalArgumentException("cellCode must not be blank");
        }
        return cellCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isAllowedTransition(LockerCellStatus currentStatus, LockerCellStatus targetStatus) {
        return switch (currentStatus) {
            case AVAILABLE -> targetStatus == LockerCellStatus.MAINTENANCE
                    || targetStatus == LockerCellStatus.DISABLED;
            case MAINTENANCE, DISABLED -> targetStatus == LockerCellStatus.AVAILABLE;
            case OCCUPIED -> false;
        };
    }
}
