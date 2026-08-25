package com.lockerflow.entity;

import com.lockerflow.entity.enums.PickupCodeStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "pickup_codes",
        indexes = {
                @Index(name = "idx_pickup_codes_parcel_status", columnList = "parcel_id,status"),
                @Index(name = "idx_pickup_codes_status_expires", columnList = "status,expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickupCode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PickupCodeStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public PickupCode(Parcel parcel, String codeHash, Instant expiresAt) {
        this.parcel = Objects.requireNonNull(parcel, "parcel must not be null");
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("codeHash must not be blank");
        }
        this.codeHash = codeHash;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = PickupCodeStatus.ACTIVE;
    }

    public void markUsed(Instant usedAt) {
        if (status != PickupCodeStatus.ACTIVE) {
            throw new IllegalStateException("Only an active pickup code can be used");
        }
        this.usedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
        this.status = PickupCodeStatus.USED;
    }

    public void markExpired() {
        if (status != PickupCodeStatus.ACTIVE) {
            throw new IllegalStateException("Only an active pickup code can expire");
        }
        this.status = PickupCodeStatus.EXPIRED;
    }

    public void revoke() {
        if (status != PickupCodeStatus.ACTIVE) {
            throw new IllegalStateException("Only an active pickup code can be revoked");
        }
        this.status = PickupCodeStatus.REVOKED;
    }
}
