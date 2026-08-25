package com.lockerflow.repository;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PickupCodeRepositoryIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private PickupCodeRepository pickupCodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV2CreatesPickupCodesTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'pickup_codes'",
                Integer.class
        );

        assertThat(tableCount).isOne();
    }

    @Test
    void savesHashedActiveCodeWithTimestamps() {
        Parcel parcel = storedParcel("PICKUP-REPO-1", 1);
        PickupCode saved = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, "$2a$10$not-a-raw-code", parcel.getExpiresAt())
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PickupCodeStatus.ACTIVE);
        assertThat(saved.getCodeHash()).doesNotContain("004271");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    void findsLatestActiveCodeForParcelAndKeepsHistory() {
        Parcel parcel = storedParcel("PICKUP-REPO-2", 2);
        PickupCode used = new PickupCode(parcel, "older-hash", parcel.getExpiresAt());
        used.markUsed(Instant.now());
        pickupCodeRepository.saveAndFlush(used);
        PickupCode active = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, "latest-hash", parcel.getExpiresAt())
        );

        assertThat(pickupCodeRepository.findFirstByParcelIdAndStatusOrderByCreatedAtDescIdDesc(
                parcel.getId(), PickupCodeStatus.ACTIVE
        )).contains(active);
        assertThat(pickupCodeRepository.findByParcelIdOrderByCreatedAtDescIdDesc(parcel.getId()))
                .hasSize(2)
                .extracting(PickupCode::getStatus)
                .containsExactly(PickupCodeStatus.ACTIVE, PickupCodeStatus.USED);
    }

    @Test
    void databaseRejectsPickupCodeWithMissingParcelForeignKey() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into pickup_codes
                            (parcel_id, code_hash, status, expires_at, used_at, created_at, updated_at)
                        values (?, ?, ?, ?, null, ?, ?)
                        """,
                999999L, "hash", "ACTIVE", now.plusSeconds(3600), now, now
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Parcel storedParcel(String trackingNumber, int suffix) {
        User customer = userRepository.save(new User(
                "repo.customer." + suffix,
                "repo.customer." + suffix + "@example.com",
                String.format("136%08d", suffix),
                "hashed",
                Role.CUSTOMER
        ));
        User courier = userRepository.save(new User(
                "repo.courier." + suffix,
                "repo.courier." + suffix + "@example.com",
                String.format("135%08d", suffix),
                "hashed",
                Role.COURIER
        ));
        LockerStation station = stationRepository.save(
                new LockerStation("Pickup Repo " + suffix, "Address " + suffix)
        );
        LockerCell cell = cellRepository.save(new LockerCell(station, "R0" + suffix, LockerSize.SMALL));
        Parcel parcel = new Parcel(trackingNumber, customer, courier, LockerSize.SMALL);
        Instant storedAt = Instant.now();
        parcel.storeIn(cell, storedAt, storedAt.plusSeconds(7200));
        return parcelRepository.saveAndFlush(parcel);
    }
}
