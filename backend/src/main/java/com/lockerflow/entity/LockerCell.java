package com.lockerflow.entity;

import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "locker_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_locker_cells_station_code",
                columnNames = {"station_id", "cell_code"}
        ),
        indexes = {
                @Index(name = "idx_locker_cells_station", columnList = "station_id"),
                @Index(name = "idx_locker_cells_status_size", columnList = "status,size")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LockerCell extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private LockerStation station;

    @Column(name = "cell_code", nullable = false, length = 20)
    private String cellCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LockerSize size;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LockerCellStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    public LockerCell(LockerStation station, String cellCode, LockerSize size) {
        this.station = Objects.requireNonNull(station, "station must not be null");
        if (cellCode == null || cellCode.isBlank()) {
            throw new IllegalArgumentException("cellCode must not be blank");
        }
        this.cellCode = cellCode.trim().toUpperCase(Locale.ROOT);
        this.size = Objects.requireNonNull(size, "size must not be null");
        this.status = LockerCellStatus.AVAILABLE;
    }

    public void changeStatus(LockerCellStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }
}

