package com.lockerflow.scheduling;

import com.lockerflow.service.ExpirationResult;
import com.lockerflow.service.ExpirationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpirationSchedulerTests {

    @Test
    void triggerDelegatesExactlyOnceWithoutWaiting() {
        ExpirationService service = mock(ExpirationService.class);
        when(service.processExpired()).thenReturn(new ExpirationResult(Instant.EPOCH, 2, 3));
        ExpirationScheduler scheduler = new ExpirationScheduler(service);

        scheduler.runExpirationProcessing();

        verify(service).processExpired();
    }
}
