package com.lockerflow.entity;

import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.ParcelStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "parcels",
        uniqueConstraints = @UniqueConstraint(name = "uk_parcels_tracking_number", columnNames = "tracking_number"),
        indexes = {
                @Index(name = "idx_parcels_customer", columnList = "customer_id"),
                @Index(name = "idx_parcels_courier", columnList = "courier_id"),
                @Index(name = "idx_parcels_locker_cell", columnList = "locker_cell_id"),
                @Index(name = "idx_parcels_status", columnList = "status"),
                @Index(name = "idx_parcels_status_expires", columnList = "status,expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parcel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private User courier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locker_cell_id")
    private LockerCell lockerCell;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LockerSize size;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ParcelStatus status;

    @Column(name = "stored_at")
    private Instant storedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public Parcel(String trackingNumber, User customer, User courier, LockerSize size) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        this.trackingNumber = trackingNumber.trim().toUpperCase(Locale.ROOT);
        this.customer = Objects.requireNonNull(customer, "customer must not be null");
        this.courier = Objects.requireNonNull(courier, "courier must not be null");
        this.size = Objects.requireNonNull(size, "size must not be null");
        this.status = ParcelStatus.CREATED;
    }

    public void storeIn(LockerCell lockerCell, Instant storedAt, Instant expiresAt) {
        this.lockerCell = Objects.requireNonNull(lockerCell, "lockerCell must not be null");
        this.storedAt = Objects.requireNonNull(storedAt, "storedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(storedAt)) {
            throw new IllegalArgumentException("expiresAt must be after storedAt");
        }
        this.status = ParcelStatus.STORED;
    }

    public void pickUp(Instant pickedUpAt) {
        if (status != ParcelStatus.STORED) {
            throw new IllegalStateException("Only a stored parcel can be picked up");
        }
        this.pickedUpAt = Objects.requireNonNull(pickedUpAt, "pickedUpAt must not be null");
        this.status = ParcelStatus.PICKED_UP;
    }

    public void expire() {
        if (status != ParcelStatus.STORED) {
            throw new IllegalStateException("Only a stored parcel can expire");
        }
        this.status = ParcelStatus.EXPIRED;
    }
}
