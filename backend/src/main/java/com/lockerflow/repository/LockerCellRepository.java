package com.lockerflow.repository;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface LockerCellRepository extends JpaRepository<LockerCell, Long> {

    Optional<LockerCell> findByStationIdAndCellCodeIgnoreCase(Long stationId, String cellCode);

    Optional<LockerCell> findByIdAndStationId(Long id, Long stationId);

    boolean existsByStationIdAndCellCodeIgnoreCase(Long stationId, String cellCode);

    boolean existsByStationIdAndStatus(Long stationId, LockerCellStatus status);

    List<LockerCell> findByStationIdOrderByCellCodeAsc(Long stationId);

    List<LockerCell> findByStationIdInOrderByStationIdAscCellCodeAsc(Collection<Long> stationIds);

    List<LockerCell> findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
            Long stationId,
            LockerCellStatus status,
            LockerSize size
    );
}
