package com.lockerflow.controller;

import com.lockerflow.entity.Parcel;
import com.lockerflow.entity.User;
import com.lockerflow.entity.enums.LockerSize;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.entity.enums.UserStatus;
import com.lockerflow.repository.ParcelRepository;
import com.lockerflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParcelReadApiIntegrationTests {

    private static final Instant OLDER = Instant.parse("2026-08-22T10:00:00Z");
    private static final Instant NEWER = Instant.parse("2026-08-23T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Test
    void courierReadsOnlyOwnParcelsInDescendingCreationOrder() throws Exception {
        User customer = saveUser("read.customer", Role.CUSTOMER, 1);
        User courier = saveUser("read.courier", Role.COURIER, 2);
        User otherCourier = saveUser("read.other.courier", Role.COURIER, 3);
        saveParcel("READ-COURIER-OLD", customer, courier, OLDER);
        saveParcel("READ-COURIER-NEW", customer, courier, NEWER);
        saveParcel("READ-COURIER-OTHER", customer, otherCourier, NEWER);

        mockMvc.perform(get("/api/courier/parcels").with(actor(courier, Role.COURIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trackingNumber").value("READ-COURIER-NEW"))
                .andExpect(jsonPath("$[1].trackingNumber").value("READ-COURIER-OLD"))
                .andExpect(jsonPath("$[0].courierId").value(courier.getId()))
                .andExpect(jsonPath("$[1].courierId").value(courier.getId()));
    }

    @Test
    void customerReadsOnlyOwnParcelsInDescendingCreationOrder() throws Exception {
        User customer = saveUser("read.customer", Role.CUSTOMER, 11);
        User otherCustomer = saveUser("read.other.customer", Role.CUSTOMER, 12);
        User courier = saveUser("read.courier", Role.COURIER, 13);
        saveParcel("READ-CUSTOMER-OLD", customer, courier, OLDER);
        saveParcel("READ-CUSTOMER-NEW", customer, courier, NEWER);
        saveParcel("READ-CUSTOMER-OTHER", otherCustomer, courier, NEWER);

        mockMvc.perform(get("/api/customer/parcels").with(actor(customer, Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trackingNumber").value("READ-CUSTOMER-NEW"))
                .andExpect(jsonPath("$[1].trackingNumber").value("READ-CUSTOMER-OLD"))
                .andExpect(jsonPath("$[0].customerId").value(customer.getId()))
                .andExpect(jsonPath("$[1].customerId").value(customer.getId()));
    }

    @Test
    void parcelReadsRejectTheWrongJwtRole() throws Exception {
        mockMvc.perform(get("/api/courier/parcels").with(subject("1", Role.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/parcels").with(subject("1", Role.COURIER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void parcelReadsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/courier/parcels"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customer/parcels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parcelReadsRejectMissingDatabaseActors() throws Exception {
        mockMvc.perform(get("/api/courier/parcels").with(subject("999999", Role.COURIER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication"));
        mockMvc.perform(get("/api/customer/parcels").with(subject("999999", Role.CUSTOMER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication"));
    }

    @Test
    void parcelReadsRejectDisabledOrDatabaseRoleMismatchedActors() throws Exception {
        User disabledCourier = saveUser("read.disabled", Role.COURIER, 21);
        disabledCourier.changeStatus(UserStatus.DISABLED);
        userRepository.flush();
        User databaseCustomer = saveUser("read.role.mismatch", Role.CUSTOMER, 22);

        mockMvc.perform(get("/api/courier/parcels").with(actor(disabledCourier, Role.COURIER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication"));
        mockMvc.perform(get("/api/courier/parcels").with(actor(databaseCustomer, Role.COURIER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid authentication"));
    }

    private User saveUser(String username, Role role, int suffix) {
        return userRepository.saveAndFlush(new User(
                username,
                username + "@example.com",
                String.format("137%08d", suffix),
                "hashed",
                role
        ));
    }

    private void saveParcel(
            String trackingNumber,
            User customer,
            User courier,
            Instant createdAt
    ) {
        Parcel parcel = new Parcel(trackingNumber, customer, courier, LockerSize.SMALL);
        ReflectionTestUtils.setField(parcel, "createdAt", createdAt);
        ReflectionTestUtils.setField(parcel, "updatedAt", createdAt);
        parcelRepository.saveAndFlush(parcel);
    }

    private RequestPostProcessor actor(User user, Role tokenRole) {
        return subject(user.getId().toString(), tokenRole);
    }

    private RequestPostProcessor subject(String subject, Role role) {
        return jwt()
                .jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
