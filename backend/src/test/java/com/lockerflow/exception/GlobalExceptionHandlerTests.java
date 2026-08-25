package com.lockerflow.exception;

import com.lockerflow.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    @Test
    void pessimisticLockCompetitionReturnsSafeConflictWithoutDatabaseDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/customer/parcels/1/pickup");

        ResponseEntity<ErrorResponse> response = handler.handlePessimisticLockingFailure(
                new PessimisticLockingFailureException("database lock internals"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Resource is currently being processed by another request")
                .doesNotContain("database", "lock internals");
    }
}
