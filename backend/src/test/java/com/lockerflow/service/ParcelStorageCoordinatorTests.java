package com.lockerflow.service;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelStorageCoordinatorTests {

    private static final Long COURIER_ID = 7L;

    @Mock
    private ParcelStorageTransactionService transactionService;

    private ParcelService coordinator;
    private CreateParcelRequest request;
    private StoreParcelResponse response;

    @BeforeEach
    void setUp() {
        coordinator = new ParcelService(transactionService);
        request = mock(CreateParcelRequest.class);
        response = mock(StoreParcelResponse.class);
    }

    @Test
    void firstAttemptSuccessInvokesTransactionalAttemptOnce() {
        when(transactionService.storeParcel(request, COURIER_ID)).thenReturn(response);

        assertThat(coordinator.storeParcel(request, COURIER_ID)).isSameAs(response);

        verify(transactionService).storeParcel(request, COURIER_ID);
    }

    @Test
    void firstOptimisticFailureThenSuccessInvokesTwoFreshAttempts() {
        when(transactionService.storeParcel(request, COURIER_ID))
                .thenThrow(optimisticConflict())
                .thenReturn(response);

        assertThat(coordinator.storeParcel(request, COURIER_ID)).isSameAs(response);

        verify(transactionService, times(2)).storeParcel(request, COURIER_ID);
    }

    @Test
    void firstTwoOptimisticFailuresThenSuccessInvokesThreeAttempts() {
        when(transactionService.storeParcel(request, COURIER_ID))
                .thenThrow(optimisticConflict())
                .thenThrow(optimisticConflict())
                .thenReturn(response);

        assertThat(coordinator.storeParcel(request, COURIER_ID)).isSameAs(response);

        verify(transactionService, times(3)).storeParcel(request, COURIER_ID);
    }

    @Test
    void maximumOptimisticFailuresBecomeSafeConflict() {
        when(transactionService.storeParcel(request, COURIER_ID))
                .thenThrow(optimisticConflict());

        assertThatThrownBy(() -> coordinator.storeParcel(request, COURIER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Locker allocation conflicted with another request; please retry");
        verify(transactionService, times(ParcelService.MAX_ALLOCATION_ATTEMPTS))
                .storeParcel(request, COURIER_ID);
    }

    @Test
    void businessConflictIsNotRetried() {
        when(transactionService.storeParcel(request, COURIER_ID))
                .thenThrow(new ConflictException("No suitable locker cell is available"));

        assertThatThrownBy(() -> coordinator.storeParcel(request, COURIER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No suitable locker cell is available");
        verify(transactionService).storeParcel(request, COURIER_ID);
    }

    @Test
    void dataIntegrityViolationIsNotRetried() {
        when(transactionService.storeParcel(request, COURIER_ID))
                .thenThrow(new DataIntegrityViolationException("duplicate tracking number"));

        assertThatThrownBy(() -> coordinator.storeParcel(request, COURIER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(transactionService).storeParcel(request, COURIER_ID);
    }

    @Test
    void eachAttemptMethodRequiresAnIndependentTransaction() throws Exception {
        Transactional transactional = ParcelStorageTransactionService.class
                .getMethod("storeParcel", CreateParcelRequest.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private ObjectOptimisticLockingFailureException optimisticConflict() {
        return new ObjectOptimisticLockingFailureException("LockerCell", 1L);
    }
}
