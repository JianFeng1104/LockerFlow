package com.lockerflow.service;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.repository.LockerCellRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LockerAllocationService {

    private final LockerCellRepository cellRepository;

    public LockerCell allocate(LockerStation station, LockerSize parcelSize) {
        Objects.requireNonNull(station, "station must not be null");
        Objects.requireNonNull(parcelSize, "parcelSize must not be null");
        if (station.getStatus() != LockerStationStatus.ACTIVE) {
            throw new ConflictException("Locker station is not available for parcel storage");
        }

        for (LockerSize candidateSize : compatibleSizes(parcelSize)) {
            List<LockerCell> candidates = cellRepository
                    .findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                            station.getId(),
                            LockerCellStatus.AVAILABLE,
                            candidateSize
                    );
            if (!candidates.isEmpty()) {
                return candidates.getFirst();
            }
        }

        throw new ConflictException(
                "No suitable locker cell is available for parcel size %s".formatted(parcelSize)
        );
    }

    private List<LockerSize> compatibleSizes(LockerSize parcelSize) {
        return switch (parcelSize) {
            case SMALL -> List.of(LockerSize.SMALL, LockerSize.MEDIUM, LockerSize.LARGE);
            case MEDIUM -> List.of(LockerSize.MEDIUM, LockerSize.LARGE);
            case LARGE -> List.of(LockerSize.LARGE);
        };
    }
}
