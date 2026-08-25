package com.lockerflow.dto.response;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;

import java.util.List;

public record LockerGridResponse(
        StationInfo station,
        Summary summary,
        List<CellInfo> cells
) {
    public static LockerGridResponse from(LockerStation station, List<LockerCell> cells) {
        return new LockerGridResponse(
                new StationInfo(station.getId(), station.getName(), station.getAddress(), station.getStatus()),
                new Summary(
                        cells.size(),
                        count(cells, LockerCellStatus.AVAILABLE),
                        count(cells, LockerCellStatus.OCCUPIED),
                        count(cells, LockerCellStatus.MAINTENANCE),
                        count(cells, LockerCellStatus.DISABLED)
                ),
                cells.stream().map(CellInfo::from).toList()
        );
    }

    private static long count(List<LockerCell> cells, LockerCellStatus status) {
        return cells.stream().filter(cell -> cell.getStatus() == status).count();
    }

    public record StationInfo(
            Long id,
            String name,
            String address,
            LockerStationStatus status
    ) {
    }

    public record Summary(
            long total,
            long available,
            long occupied,
            long maintenance,
            long disabled
    ) {
    }

    public record CellInfo(
            Long id,
            String cellCode,
            LockerSize size,
            LockerCellStatus status,
            long version
    ) {
        private static CellInfo from(LockerCell cell) {
            return new CellInfo(
                    cell.getId(),
                    cell.getCellCode(),
                    cell.getSize(),
                    cell.getStatus(),
                    cell.getVersion()
            );
        }
    }
}

