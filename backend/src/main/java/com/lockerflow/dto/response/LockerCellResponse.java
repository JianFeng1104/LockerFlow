package com.lockerflow.dto.response;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;

import java.time.Instant;

public record LockerCellResponse(
        Long id,
        Long stationId,
        String cellCode,
        LockerSize size,
        LockerCellStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static LockerCellResponse from(LockerCell cell) {
        return new LockerCellResponse(
                cell.getId(),
                cell.getStation().getId(),
                cell.getCellCode(),
                cell.getSize(),
                cell.getStatus(),
                cell.getVersion(),
                cell.getCreatedAt(),
                cell.getUpdatedAt()
        );
    }
}

