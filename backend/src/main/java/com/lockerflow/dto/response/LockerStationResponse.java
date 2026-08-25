package com.lockerflow.dto.response;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerStationStatus;

import java.time.Instant;
import java.util.Collection;

public record LockerStationResponse(
        Long id,
        String name,
        String address,
        LockerStationStatus status,
        long totalCells,
        long availableCells,
        long occupiedCells,
        long maintenanceCells,
        long disabledCells,
        Instant createdAt,
        Instant updatedAt
) {
    public static LockerStationResponse from(LockerStation station, Collection<LockerCell> cells) {
        return new LockerStationResponse(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getStatus(),
                cells.size(),
                count(cells, LockerCellStatus.AVAILABLE),
                count(cells, LockerCellStatus.OCCUPIED),
                count(cells, LockerCellStatus.MAINTENANCE),
                count(cells, LockerCellStatus.DISABLED),
                station.getCreatedAt(),
                station.getUpdatedAt()
        );
    }

    private static long count(Collection<LockerCell> cells, LockerCellStatus status) {
        return cells.stream().filter(cell -> cell.getStatus() == status).count();
    }
}

