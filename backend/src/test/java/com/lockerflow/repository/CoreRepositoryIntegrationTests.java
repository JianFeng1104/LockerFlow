package com.lockerflow.repository;

import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.LockerStationStatus;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoreRepositoryIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void userCanBeFoundByUsernameIgnoringCase() {
        User saved = userRepository.saveAndFlush(user("customer.one", Role.CUSTOMER, 1));

        assertThat(userRepository.findByUsernameIgnoreCase("CUSTOMER.ONE"))
                .contains(saved);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void userCanBeFoundByEmailIgnoringCase() {
        User saved = userRepository.saveAndFlush(user("customer.two", Role.CUSTOMER, 2));

        assertThat(userRepository.findByEmailIgnoreCase("CUSTOMER.TWO@EXAMPLE.COM"))
                .contains(saved);
    }

    @Test
    void duplicateUsernameIsRejectedByDatabase() {
        userRepository.saveAndFlush(user("same-user", Role.CUSTOMER, 3));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("same-user", "different@example.com", "13800000104", "hashed", Role.CUSTOMER)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stationsCanBeFilteredByOperationalStatus() {
        LockerStation active = stationRepository.save(new LockerStation("Station A", "Block A"));
        LockerStation maintenance = new LockerStation("Station B", "Block B");
        maintenance.changeStatus(LockerStationStatus.MAINTENANCE);
        stationRepository.saveAndFlush(maintenance);

        assertThat(stationRepository.findByStatusOrderByNameAsc(LockerStationStatus.ACTIVE))
                .extracting(LockerStation::getId)
                .containsExactly(active.getId());
    }

    @Test
    void stationCellsAreReturnedInCodeOrder() {
        LockerStation station = stationRepository.save(new LockerStation("Station A", "Block A"));
        cellRepository.save(new LockerCell(station, "A03", LockerSize.LARGE));
        cellRepository.save(new LockerCell(station, "A01", LockerSize.SMALL));
        cellRepository.saveAndFlush(new LockerCell(station, "A02", LockerSize.MEDIUM));

        assertThat(cellRepository.findByStationIdOrderByCellCodeAsc(station.getId()))
                .extracting(LockerCell::getCellCode)
                .containsExactly("A01", "A02", "A03");
    }

    @Test
    void availableCellsCanBeFilteredBySize() {
        LockerStation station = stationRepository.save(new LockerStation("Station A", "Block A"));
        LockerCell available = cellRepository.save(new LockerCell(station, "A01", LockerSize.SMALL));
        LockerCell occupied = new LockerCell(station, "A02", LockerSize.SMALL);
        occupied.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(occupied);

        assertThat(cellRepository.findByStationIdAndStatusAndSizeOrderByCellCodeAsc(
                station.getId(), LockerCellStatus.AVAILABLE, LockerSize.SMALL
        )).extracting(LockerCell::getId).containsExactly(available.getId());
    }

    @Test
    void cellCodeMustBeUniqueWithinStation() {
        LockerStation station = stationRepository.save(new LockerStation("Station A", "Block A"));
        cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));

        assertThatThrownBy(() -> cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.LARGE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCellCodeIsAllowedAtDifferentStations() {
        LockerStation first = stationRepository.save(new LockerStation("Station A", "Block A"));
        LockerStation second = stationRepository.save(new LockerStation("Station B", "Block B"));

        cellRepository.save(new LockerCell(first, "A01", LockerSize.SMALL));
        cellRepository.saveAndFlush(new LockerCell(second, "A01", LockerSize.SMALL));

        assertThat(cellRepository.count()).isEqualTo(2);
    }

    @Test
    void changingCellStatusIncrementsOptimisticLockVersion() {
        LockerStation station = stationRepository.save(new LockerStation("Station A", "Block A"));
        LockerCell cell = cellRepository.saveAndFlush(new LockerCell(station, "A01", LockerSize.SMALL));
        long originalVersion = cell.getVersion();

        cell.changeStatus(LockerCellStatus.MAINTENANCE);
        cellRepository.flush();
        entityManager.clear();

        LockerCell reloaded = cellRepository.findById(cell.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(originalVersion + 1);
        assertThat(reloaded.getStatus()).isEqualTo(LockerCellStatus.MAINTENANCE);
    }

    @Test
    void parcelCanBeFoundByTrackingNumberIgnoringCase() {
        StoredParcelFixture fixture = storedParcel("SF-2001", Instant.now().plus(2, ChronoUnit.DAYS), 10);

        assertThat(parcelRepository.findByTrackingNumberIgnoreCase("sf-2001"))
                .get()
                .satisfies(parcel -> {
                    assertThat(parcel.getCustomer().getId()).isEqualTo(fixture.customer().getId());
                    assertThat(parcel.getCourier().getId()).isEqualTo(fixture.courier().getId());
                    assertThat(parcel.getLockerCell().getId()).isEqualTo(fixture.cell().getId());
                    assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.STORED);
                });
    }

    @Test
    void parcelOwnershipQueriesSeparateCustomerAndCourier() {
        StoredParcelFixture first = storedParcel("SF-2002", Instant.now().plus(2, ChronoUnit.DAYS), 20);
        StoredParcelFixture second = storedParcel("SF-2003", Instant.now().plus(2, ChronoUnit.DAYS), 30);

        assertThat(parcelRepository.findByCustomerIdOrderByCreatedAtDesc(first.customer().getId()))
                .extracting(Parcel::getTrackingNumber)
                .containsExactly("SF-2002");
        assertThat(parcelRepository.findByCourierIdOrderByCreatedAtDesc(second.courier().getId()))
                .extracting(Parcel::getTrackingNumber)
                .containsExactly("SF-2003");
    }

    @Test
    void expirationQueryIncludesCutoffAndOrdersByExpiryThenId() {
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        StoredParcelFixture expired = storedParcel("SF-2004", now.minus(1, ChronoUnit.HOURS), 40);
        StoredParcelFixture boundary = storedParcel("SF-2004-B", now, 41);
        storedParcel("SF-2005", now.plus(1, ChronoUnit.DAYS), 50);

        List<Parcel> result = parcelRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        ParcelStatus.STORED,
                        now
                );

        assertThat(result).extracting(Parcel::getId).containsExactly(
                expired.parcel().getId(),
                boundary.parcel().getId()
        );
    }

    @Test
    void duplicateTrackingNumberIsRejectedByDatabase() {
        StoredParcelFixture fixture = storedParcel("SF-2006", Instant.now().plus(1, ChronoUnit.DAYS), 60);
        User anotherCustomer = userRepository.save(user("customer.61", Role.CUSTOMER, 61));
        Parcel duplicate = new Parcel("sf-2006", anotherCustomer, fixture.courier(), LockerSize.SMALL);

        assertThatThrownBy(() -> parcelRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private StoredParcelFixture storedParcel(String trackingNumber, Instant expiresAt, int suffix) {
        User customer = userRepository.save(user("customer." + suffix, Role.CUSTOMER, suffix));
        User courier = userRepository.save(user("courier." + suffix, Role.COURIER, suffix + 100));
        LockerStation station = stationRepository.save(new LockerStation("Station " + suffix, "Block " + suffix));
        LockerCell cell = cellRepository.save(new LockerCell(station, "A" + suffix, LockerSize.MEDIUM));
        Parcel parcel = new Parcel(trackingNumber, customer, courier, LockerSize.MEDIUM);
        Instant storedAt = expiresAt.minus(2, ChronoUnit.DAYS);
        parcel.storeIn(cell, storedAt, expiresAt);
        parcelRepository.saveAndFlush(parcel);
        return new StoredParcelFixture(customer, courier, cell, parcel);
    }

    private User user(String username, Role role, int suffix) {
        return new User(
                username,
                username + "@example.com",
                String.format("138%08d", suffix),
                "hashed",
                role
        );
    }

    private record StoredParcelFixture(User customer, User courier, LockerCell cell, Parcel parcel) {
    }
}
