package com.lockerflow.service;

import com.lockerflow.dto.request.PickupParcelRequest;
import com.lockerflow.dto.response.ParcelResponse;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@Import(LifecycleCompetitionIntegrationTests.RaceClockConfig.class)
class LifecycleCompetitionIntegrationTests {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-23T12:00:00Z");
    private static final Instant PICKUP_NOW = EXPIRES_AT.minusSeconds(1);
    private static final long TIMEOUT_SECONDS = 10;
    private static final String RAW_CODE = "004271";

    @Autowired
    private PickupService pickupService;

    @Autowired
    private ExpirationService expirationService;

    @Autowired
    private PickupCodeHasher pickupCodeHasher;

    @Autowired
    private RaceClock raceClock;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockerStationRepository stationRepository;

    @Autowired
    private LockerCellRepository cellRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private ParcelRepository parcelRepository;

    @MockitoSpyBean
    private PickupCodeRepository pickupCodeRepository;

    @BeforeEach
    void configureClock() {
        raceClock.setPickupInstant(PICKUP_NOW);
        raceClock.setExpirationInstant(EXPIRES_AT);
    }

    @AfterEach
    void cleanCommittedFixtureData() {
        reset(parcelRepository, pickupCodeRepository);
        pickupCodeRepository.deleteAll();
        parcelRepository.deleteAll();
        cellRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentPickupAllowsExactlyOneSuccessAndRejectsEveryLaterAttempt() throws Exception {
        Fixture fixture = fixture("PICKUP-RACE");
        AtomicInteger parcelLockCalls = new AtomicInteger();
        CountDownLatch firstParcelLocked = new CountDownLatch(1);
        CountDownLatch secondPickupStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstPickup = new CountDownLatch(1);
        doAnswer(invocation -> {
            int order = parcelLockCalls.incrementAndGet();
            if (order == 1) {
                Object result = findParcelForUpdate(fixture.parcel().getId());
                firstParcelLocked.countDown();
                await(releaseFirstPickup);
                return result;
            }
            secondPickupStarted.countDown();
            return findParcelForUpdate(fixture.parcel().getId());
        }).when(parcelRepository).findByIdForUpdate(fixture.parcel().getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        try {
            Future<PickupOutcome> first = executor.submit(
                    pickupTask(startBarrier, fixture.parcel().getId(), fixture.customer().getId())
            );
            Future<PickupOutcome> second = executor.submit(
                    pickupTask(startBarrier, fixture.parcel().getId(), fixture.customer().getId())
            );
            assertThat(firstParcelLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(secondPickupStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            releaseFirstPickup.countDown();

            List<PickupOutcome> outcomes = List.of(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
            assertThat(outcomes).filteredOn(PickupOutcome::successful).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.successful()).singleElement()
                    .satisfies(outcome -> assertThat(outcome.failure()).isInstanceOf(ConflictException.class));
        } finally {
            releaseFirstPickup.countDown();
            shutdown(executor);
        }

        assertPickedUpState(fixture);
        assertThatThrownBy(() -> pickupService.pickUp(
                fixture.parcel().getId(),
                fixture.customer().getId(),
                new PickupParcelRequest(RAW_CODE)
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Parcel is not available for pickup");
        assertPickedUpState(fixture);
    }

    @Test
    void pickupLockFirstProducesOnlyPickedUpUsedAvailableState() throws Exception {
        Fixture fixture = fixture("PICKUP-WINS");
        CountDownLatch pickupParcelLocked = new CountDownLatch(1);
        CountDownLatch expirationQueryStarted = new CountDownLatch(1);
        CountDownLatch releasePickup = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = findParcelForUpdate(fixture.parcel().getId());
            pickupParcelLocked.countDown();
            await(releasePickup);
            return result;
        }).when(parcelRepository).findByIdForUpdate(fixture.parcel().getId());
        doAnswer(invocation -> {
            expirationQueryStarted.countDown();
            return findExpiredParcelsForUpdate(invocation.getArgument(1));
        }).when(parcelRepository)
                .findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        eq(ParcelStatus.STORED),
                        any(Instant.class)
                );

        ExecutorService pickupExecutor = namedExecutor("pickup-winner");
        ExecutorService expirationExecutor = namedExecutor("expiration-after-pickup");
        try {
            Future<ParcelResponse> pickup = pickupExecutor.submit(() -> pickupService.pickUp(
                    fixture.parcel().getId(),
                    fixture.customer().getId(),
                    new PickupParcelRequest(RAW_CODE)
            ));
            assertThat(pickupParcelLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<ExpirationResult> expiration = expirationExecutor.submit(expirationService::processExpired);
            assertThat(expirationQueryStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            releasePickup.countDown();

            assertThat(pickup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status())
                    .isEqualTo(ParcelStatus.PICKED_UP);
            ExpirationResult result = expiration.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(result.expiredParcels()).isZero();
            assertThat(result.expiredPickupCodes()).isZero();
        } finally {
            releasePickup.countDown();
            shutdown(pickupExecutor);
            shutdown(expirationExecutor);
        }

        assertPickedUpState(fixture);
    }

    @Test
    void expirationLockFirstProducesOnlyExpiredExpiredOccupiedState() throws Exception {
        Fixture fixture = fixture("EXPIRATION-WINS");
        CountDownLatch expirationParcelLocked = new CountDownLatch(1);
        CountDownLatch pickupQueryStarted = new CountDownLatch(1);
        CountDownLatch releaseExpiration = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = findExpiredParcelsForUpdate(invocation.getArgument(1));
            expirationParcelLocked.countDown();
            await(releaseExpiration);
            return result;
        }).when(parcelRepository)
                .findForUpdateByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        eq(ParcelStatus.STORED),
                        any(Instant.class)
                );
        doAnswer(invocation -> {
            pickupQueryStarted.countDown();
            return findParcelForUpdate(fixture.parcel().getId());
        }).when(parcelRepository).findByIdForUpdate(fixture.parcel().getId());

        ExecutorService expirationExecutor = namedExecutor("expiration-winner");
        ExecutorService pickupExecutor = namedExecutor("pickup-after-expiration");
        try {
            Future<ExpirationResult> expiration = expirationExecutor.submit(expirationService::processExpired);
            assertThat(expirationParcelLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<PickupOutcome> pickup = pickupExecutor.submit(
                    pickupTask(null, fixture.parcel().getId(), fixture.customer().getId())
            );
            assertThat(pickupQueryStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            releaseExpiration.countDown();

            ExpirationResult result = expiration.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(result.expiredParcels()).isOne();
            assertThat(result.expiredPickupCodes()).isOne();
            PickupOutcome pickupResult = pickup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(pickupResult.successful()).isFalse();
            assertThat(pickupResult.failure()).isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid or expired pickup code");
        } finally {
            releaseExpiration.countDown();
            shutdown(expirationExecutor);
            shutdown(pickupExecutor);
        }

        Parcel parcel = parcelRepository.findById(fixture.parcel().getId()).orElseThrow();
        PickupCode code = pickupCodeRepository.findById(fixture.code().getId()).orElseThrow();
        LockerCell cell = cellRepository.findById(fixture.cell().getId()).orElseThrow();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.EXPIRED);
        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.EXPIRED);
        assertThat(code.getUsedAt()).isNull();
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.OCCUPIED);
    }

    private java.util.concurrent.Callable<PickupOutcome> pickupTask(
            CyclicBarrier startBarrier,
            Long parcelId,
            Long customerId
    ) {
        return () -> {
            if (startBarrier != null) {
                startBarrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            try {
                return new PickupOutcome(
                        pickupService.pickUp(parcelId, customerId, new PickupParcelRequest(RAW_CODE)),
                        null
                );
            } catch (ConflictException exception) {
                return new PickupOutcome(null, exception);
            }
        };
    }

    private void assertPickedUpState(Fixture fixture) {
        Parcel parcel = parcelRepository.findById(fixture.parcel().getId()).orElseThrow();
        PickupCode code = pickupCodeRepository.findById(fixture.code().getId()).orElseThrow();
        LockerCell cell = cellRepository.findById(fixture.cell().getId()).orElseThrow();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(parcel.getPickedUpAt()).isEqualTo(PICKUP_NOW);
        assertThat(code.getStatus()).isEqualTo(PickupCodeStatus.USED);
        assertThat(code.getUsedAt()).isEqualTo(PICKUP_NOW);
        assertThat(cell.getStatus()).isEqualTo(LockerCellStatus.AVAILABLE);
    }

    private java.util.Optional<Parcel> findParcelForUpdate(Long parcelId) {
        return java.util.Optional.ofNullable(
                entityManager.find(Parcel.class, parcelId, LockModeType.PESSIMISTIC_WRITE)
        );
    }

    private List<Parcel> findExpiredParcelsForUpdate(Instant cutoff) {
        return entityManager.createQuery(
                        """
                                select parcel
                                from Parcel parcel
                                where parcel.status = :status
                                  and parcel.expiresAt <= :cutoff
                                order by parcel.expiresAt asc, parcel.id asc
                                """,
                        Parcel.class
                )
                .setParameter("status", ParcelStatus.STORED)
                .setParameter("cutoff", cutoff)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    private Fixture fixture(String trackingNumber) {
        User customer = userRepository.saveAndFlush(new User(
                trackingNumber.toLowerCase() + ".customer",
                trackingNumber.toLowerCase() + ".customer@example.com",
                "12700000001",
                "test-hash",
                Role.CUSTOMER
        ));
        User courier = userRepository.saveAndFlush(new User(
                trackingNumber.toLowerCase() + ".courier",
                trackingNumber.toLowerCase() + ".courier@example.com",
                "12700000002",
                "test-hash",
                Role.COURIER
        ));
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation(trackingNumber + " Station", trackingNumber + " Address")
        );
        LockerCell cell = new LockerCell(station, "A01", LockerSize.SMALL);
        cell.changeStatus(LockerCellStatus.OCCUPIED);
        cellRepository.saveAndFlush(cell);
        Parcel parcel = new Parcel(trackingNumber, customer, courier, LockerSize.SMALL);
        parcel.storeIn(cell, EXPIRES_AT.minusSeconds(3600), EXPIRES_AT);
        parcelRepository.saveAndFlush(parcel);
        PickupCode code = pickupCodeRepository.saveAndFlush(
                new PickupCode(parcel, pickupCodeHasher.hash(RAW_CODE), EXPIRES_AT)
        );
        return new Fixture(customer, parcel, code, cell);
    }

    private ExecutorService namedExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, threadName));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test latch was interrupted", exception);
        }
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private record PickupOutcome(ParcelResponse response, ConflictException failure) {
        private boolean successful() {
            return response != null;
        }
    }

    private record Fixture(User customer, Parcel parcel, PickupCode code, LockerCell cell) {
    }

    @TestConfiguration
    static class RaceClockConfig {

        @Bean
        @Primary
        RaceClock raceClock() {
            return new RaceClock();
        }
    }

    static class RaceClock extends Clock {

        private volatile Instant pickupInstant = PICKUP_NOW;
        private volatile Instant expirationInstant = EXPIRES_AT;

        void setPickupInstant(Instant pickupInstant) {
            this.pickupInstant = pickupInstant;
        }

        void setExpirationInstant(Instant expirationInstant) {
            this.expirationInstant = expirationInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Thread.currentThread().getName().startsWith("expiration")
                    ? expirationInstant
                    : pickupInstant;
        }
    }
}
