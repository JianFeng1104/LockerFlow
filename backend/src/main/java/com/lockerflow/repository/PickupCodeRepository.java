package com.lockerflow.repository;

import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.enums.PickupCodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PickupCodeRepository extends JpaRepository<PickupCode, Long> {

    Optional<PickupCode> findFirstByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
            Long parcelId,
            PickupCodeStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PickupCode> findFirstForUpdateByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
            Long parcelId,
            PickupCodeStatus status
    );

    List<PickupCode> findByParcelIdOrderByCreatedAtDescIdDesc(Long parcelId);

    List<PickupCode> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            PickupCodeStatus status,
            Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PickupCode> findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            PickupCodeStatus status,
            Instant cutoff
    );
}
