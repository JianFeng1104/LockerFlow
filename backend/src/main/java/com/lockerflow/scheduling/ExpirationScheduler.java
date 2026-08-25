package com.lockerflow.scheduling;

import com.lockerflow.service.ExpirationResult;
import com.lockerflow.service.ExpirationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "lockerflow.expiration",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);

    private final ExpirationService expirationService;

    @Scheduled(
            fixedDelayString = "${lockerflow.expiration.fixed-delay:PT15M}",
            initialDelayString = "${lockerflow.expiration.initial-delay:PT1M}"
    )
    public void runExpirationProcessing() {
        ExpirationResult result = expirationService.processExpired();
        log.info(
                "Expiration processing completed: parcels={}, pickupCodes={}",
                result.expiredParcels(),
                result.expiredPickupCodes()
        );
    }
}
