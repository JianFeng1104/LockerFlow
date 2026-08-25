package com.lockerflow.entity;

import com.lockerflow.entity.enums.LockerStationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "locker_stations",
        indexes = @Index(name = "idx_locker_stations_status", columnList = "status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LockerStation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LockerStationStatus status;

    public LockerStation(String name, String address) {
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
        this.status = LockerStationStatus.ACTIVE;
    }

    public void updateDetails(String name, String address) {
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
    }

    public void changeStatus(LockerStationStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

