package com.lockerflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.entity.LockerCell;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.PickupCode;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerCellStatus;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.ParcelStatus;
import com.lockerflow.entity.enums.PickupCodeStatus;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.exception.ConflictException;
import com.lockerflow.repository.LockerCellRepository;
import com.lockerflow.repository.LockerStationRepository;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.PickupCodeRepository;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(StorageCompetitionIntegrationTests.FixedClockConfig.class)
class StorageCompetitionIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");
    private static final long FUTURE_TIMEOUT_SECONDS = 15;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockitoSpyBean
    private LockerAllocationService allocationService;

    @AfterEach
    void cleanCommittedFixtureData() {
        reset(allocationService);
        pickupCodeRepository.deleteAll();
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void twoConcurrentParcelsReallocateAcrossTwoSmallCells() throws Exception {
        Fixture fixture = fixture(List.of(
                new CellSpec("A01", LockerSize.SMALL),
                new CellSpec("A02", LockerSize.SMALL)
        ));
        coordinateFirstTwoSelections();

        List<StorageOutcome> outcomes = runConcurrently(
                storageTask(fixture, fixture.customerA(), fixture.courierA(), "PKG-CONCURRENT-A"),
                storageTask(fixture, fixture.customerB(), fixture.courierB(), "PKG-CONCURRENT-B")
        );

        assertThat(outcomes).allMatch(StorageOutcome::successful);
        assertStoredAssignments("A01", "A02");
        assertThat(activePickupCodes()).hasSize(2);
        assertThat(cellRepository.findByStationIdOrderByCellCodeAsc(fixture.station().getId()))
                .extracting(LockerCell::getStatus)
                .containsExactly(LockerCellStatus.OCCUPIED, LockerCellStatus.OCCUPIED);
    }

    @Test
    void twoConcurrentParcelsCompetingForOneCellProduceOneConflict() throws Exception {
        Fixture fixture = fixture(List.of(new CellSpec("A01", LockerSize.SMALL)));
        coordinateFirstTwoSelections();

        List<StorageOutcome> outcomes = runConcurrently(
                storageTask(fixture, fixture.customerA(), fixture.courierA(), "PKG-ONE-CELL-A"),
                storageTask(fixture, fixture.customerB(), fixture.courierB(), "PKG-ONE-CELL-B")
        );

        assertThat(outcomes).filteredOn(StorageOutcome::successful).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.successful()).singleElement()
                .satisfies(outcome -> assertThat(outcome.failure()).isInstanceOf(ConflictException.class));
        assertThat(parcelRepository.findAll()).hasSize(1)
                .allMatch(parcel -> parcel.getStatus() == ParcelStatus.STORED);
        assertThat(activePickupCodes()).hasSize(1);
        assertThat(cellRepository.findById(fixture.cells().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(LockerCellStatus.OCCUPIED);
    }

    @Test
    void retryReevaluatesBestFitAndFallsBackFromSmallToMedium() throws Exception {
        Fixture fixture = fixture(List.of(
                new CellSpec("A01", LockerSize.SMALL),
                new CellSpec("A02", LockerSize.MEDIUM)
        ));
        coordinateFirstTwoSelections();

        List<StorageOutcome> outcomes = runConcurrently(
                storageTask(fixture, fixture.customerA(), fixture.courierA(), "PKG-BEST-FIT-A"),
                storageTask(fixture, fixture.customerB(), fixture.courierB(), "PKG-BEST-FIT-B")
        );

        assertThat(outcomes).allMatch(StorageOutcome::successful);
        assertStoredAssignments("A01", "A02");
        assertThat(jdbcTemplate.queryForList(
                """
                        select locker.size
                        from parcels parcel
                        join locker_cells locker on locker.id = parcel.locker_cell_id
                        """,
                String.class
        )).containsExactlyInAnyOrder("SMALL", "MEDIUM");
    }

    @Test
    void concurrentDuplicateTrackingNumberReturnsOneHttp409WithoutRetryingConstraint() throws Exception {
        Fixture fixture = fixture(List.of(
                new CellSpec("A01", LockerSize.SMALL),
                new CellSpec("A02", LockerSize.SMALL)
        ));
        coordinateFirstTwoSelections();

        List<Integer> statuses = runConcurrently(
                httpStorageTask(fixture, fixture.customerA(), fixture.courierA(), "PKG-DUPLICATE-RACE"),
                httpStorageTask(fixture, fixture.customerB(), fixture.courierB(), "PKG-DUPLICATE-RACE")
        );

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(parcelRepository.findByTrackingNumberIgnoreCase("PKG-DUPLICATE-RACE")).isPresent();
        assertThat(parcelRepository.findAll()).hasSize(1);
        assertThat(activePickupCodes()).hasSize(1);
    }

    private void coordinateFirstTwoSelections() {
        AtomicInteger selectionCount = new AtomicInteger();
        CyclicBarrier bothSelected = new CyclicBarrier(2);
        doAnswer(invocation -> {
            LockerCell selected = (LockerCell) invocation.callRealMethod();
            if (selectionCount.incrementAndGet() <= 2) {
                await(bothSelected);
            }
            return selected;
        }).when(allocationService).allocate(any(LockerStation.class), any(LockerSize.class));
    }

    private Callable<StorageOutcome> storageTask(
            Fixture fixture,
            User customer,
            User courier,
            String trackingNumber
    ) {
        return () -> {
            try {
                StoreParcelResponse response = parcelService.storeParcel(
                        new CreateParcelRequest(
                                trackingNumber,
                                customer.getId(),
                                fixture.station().getId(),
                                LockerSize.SMALL
                        ),
                        courier.getId()
                );
                return new StorageOutcome(response, null);
            } catch (ConflictException | DataIntegrityViolationException exception) {
                return new StorageOutcome(null, exception);
            }
        };
    }

    private Callable<Integer> httpStorageTask(
            Fixture fixture,
            User customer,
            User courier,
            String trackingNumber
    ) {
        return () -> mockMvc.perform(post("/api/courier/parcels")
                        .with(jwt()
                                .jwt(token -> token.subject(courier.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trackingNumber", trackingNumber,
                                "customerId", customer.getId(),
                                "stationId", fixture.station().getId(),
                                "size", "SMALL"
                        ))))
                .andReturn().getResponse().getStatus();
    }

    private <T> List<T> runConcurrently(Callable<T> firstTask, Callable<T> secondTask) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        try {
            Future<T> first = executor.submit(() -> {
                await(startBarrier);
                return firstTask.call();
            });
            Future<T> second = executor.submit(() -> {
                await(startBarrier);
                return secondTask.call();
            });
            return List.of(
                    first.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertStoredAssignments(String... cellCodes) {
        assertThat(parcelRepository.findAll())
                .hasSize(cellCodes.length)
                .allMatch(parcel -> parcel.getStatus() == ParcelStatus.STORED);
        assertThat(jdbcTemplate.queryForList(
                """
                        select locker.cell_code
                        from parcels parcel
                        join locker_cells locker on locker.id = parcel.locker_cell_id
                        """,
                String.class
        )).containsExactlyInAnyOrder(cellCodes);
    }

    private List<PickupCode> activePickupCodes() {
        return pickupCodeRepository.findAll().stream()
                .filter(code -> code.getStatus() == PickupCodeStatus.ACTIVE)
                .toList();
    }

    private Fixture fixture(List<CellSpec> cellSpecs) {
        User customerA = saveUser("storage.customer.a", Role.CUSTOMER, "12800000001");
        User customerB = saveUser("storage.customer.b", Role.CUSTOMER, "12800000002");
        User courierA = saveUser("storage.courier.a", Role.COURIER, "12800000003");
        User courierB = saveUser("storage.courier.b", Role.COURIER, "12800000004");
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Storage Competition Station", "Storage Competition Address")
        );
        List<LockerCell> cells = cellSpecs.stream()
                .map(spec -> new LockerCell(station, spec.code(), spec.size()))
                .toList();
        cellRepository.saveAllAndFlush(cells);
        return new Fixture(customerA, customerB, courierA, courierB, station, cells);
    }

    private User saveUser(String username, Role role, String phone) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                phone,
                "test-hash",
                role
        ));
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (BrokenBarrierException | TimeoutException exception) {
            throw new IllegalStateException("Concurrent test barrier failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test barrier was interrupted", exception);
        }
    }

    private record StorageOutcome(StoreParcelResponse response, RuntimeException failure) {
        private boolean successful() {
            return response != null;
        }
    }

    private record CellSpec(String code, LockerSize size) {
    }

    private record Fixture(
            User customerA,
            User customerB,
            User courierA,
            User courierB,
            LockerStation station,
            List<LockerCell> cells
    ) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
