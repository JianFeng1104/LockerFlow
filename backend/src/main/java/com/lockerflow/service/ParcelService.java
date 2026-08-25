package com.lockerflow.service;

import com.lockerflow.dto.request.CreateParcelRequest;
import com.lockerflow.dto.response.StoreParcelResponse;
import com.lockerflow.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParcelService {

    static final int MAX_ALLOCATION_ATTEMPTS = 3;

    private static final String ALLOCATION_CONFLICT_MESSAGE =
            "Locker allocation conflicted with another request; please retry";

    private final ParcelStorageTransactionService transactionService;

    public StoreParcelResponse storeParcel(CreateParcelRequest request, Long authenticatedCourierId) {
        for (int attempt = 1; attempt <= MAX_ALLOCATION_ATTEMPTS; attempt++) {
            try {
                return transactionService.storeParcel(request, authenticatedCourierId);
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == MAX_ALLOCATION_ATTEMPTS) {
                    throw new ConflictException(ALLOCATION_CONFLICT_MESSAGE);
                }
            }
        }

        throw new ConflictException(ALLOCATION_CONFLICT_MESSAGE);
    }
}
