package com.lockerflow.repository;

import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.LockerStationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LockerStationRepository extends JpaRepository<LockerStation, Long> {

    List<LockerStation> findAllByOrderByNameAsc();

    List<LockerStation> findByStatusOrderByNameAsc(LockerStationStatus status);
}
