package com.lockerflow.repository;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.enums.ParcelStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    Optional<Parcel> findByTrackingNumberIgnoreCase(String trackingNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select parcel from Parcel parcel where parcel.id = :parcelId")
    Optional<Parcel> findByIdForUpdate(@Param("parcelId") Long parcelId);

    boolean existsByTrackingNumberIgnoreCase(String trackingNumber);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Parcel> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    List<Parcel> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            ParcelStatus status,
            Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Parcel> findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            ParcelStatus status,
            Instant cutoff
    );

    boolean existsByLockerCellIdAndStatusIn(Long lockerCellId, Collection<ParcelStatus> statuses);

    boolean existsByLockerCellStationIdAndStatusIn(Long stationId, Collection<ParcelStatus> statuses);
}
