package com.lockerflow.dto.response;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.ParcelStatus;

import java.time.Instant;

public record ParcelResponse(
        Long id,
        String trackingNumber,
        ParcelStatus status,
        LockerSize size,
        Long customerId,
        Long courierId,
        Long stationId,
        String stationName,
        String stationAddress,
        Long lockerCellId,
        String lockerCellCode,
        LockerSize lockerCellSize,
        Instant storedAt,
        Instant expiresAt,
        Instant pickedUpAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParcelResponse from(Parcel parcel) {
        LockerCell cell = parcel.getLockerCell();
        LockerStation station = cell == null ? null : cell.getStation();
        return new ParcelResponse(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getSize(),
                parcel.getCustomer().getId(),
                parcel.getCourier().getId(),
                station == null ? null : station.getId(),
                station == null ? null : station.getName(),
                station == null ? null : station.getAddress(),
                cell == null ? null : cell.getId(),
                cell == null ? null : cell.getCellCode(),
                cell == null ? null : cell.getSize(),
                parcel.getStoredAt(),
                parcel.getExpiresAt(),
                parcel.getPickedUpAt(),
                parcel.getCreatedAt(),
                parcel.getUpdatedAt()
        );
    }
}
