package com.lockerflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lockerflow.entity.LockerStation;
import com.lockerflow.entity.enums.Role;
import com.lockerflow.repository.LockerStationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityRbacIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LockerStationRepository stationRepository;

    @Test
    void anonymousRequestsReceiveConsistentJson401() throws Exception {
        assertAnonymousUnauthorized(get("/api/stations"), "/api/stations");
        assertAnonymousUnauthorized(post("/api/admin/stations"), "/api/admin/stations");
        assertAnonymousUnauthorized(post("/api/courier/parcels"), "/api/courier/parcels");
        assertAnonymousUnauthorized(post("/api/customer/parcels/1/pickup"), "/api/customer/parcels/1/pickup");
    }

    @Test
    void eachWriteNamespaceAcceptsOnlyItsOwnRole() throws Exception {
        mockMvc.perform(post("/api/admin/stations")
                        .with(role(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RBAC Station",
                                "address", "RBAC Address"
                        ))))
                .andExpect(status().isCreated());
        assertForbidden(post("/api/admin/stations").with(role(Role.COURIER)), "/api/admin/stations");
        assertForbidden(post("/api/admin/stations").with(role(Role.CUSTOMER)), "/api/admin/stations");

        mockMvc.perform(post("/api/courier/parcels")
                        .with(role(Role.COURIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertForbidden(post("/api/courier/parcels").with(role(Role.ADMIN)), "/api/courier/parcels");
        assertForbidden(post("/api/courier/parcels").with(role(Role.CUSTOMER)), "/api/courier/parcels");

        mockMvc.perform(post("/api/customer/parcels/1/pickup")
                        .with(role(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertForbidden(
                post("/api/customer/parcels/1/pickup").with(role(Role.ADMIN)),
                "/api/customer/parcels/1/pickup"
        );
        assertForbidden(
                post("/api/customer/parcels/1/pickup").with(role(Role.COURIER)),
                "/api/customer/parcels/1/pickup"
        );
    }

    @Test
    void stationReadsAllowEveryAuthenticatedRole() throws Exception {
        LockerStation station = stationRepository.saveAndFlush(
                new LockerStation("Readable Station", "Readable Address")
        );

        for (Role role : Role.values()) {
            mockMvc.perform(get("/api/stations/{stationId}", station.getId()).with(role(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(station.getId()));
        }
    }

    @Test
    void anyUnlistedRequestIsDenied() throws Exception {
        assertForbidden(get("/api/unlisted").with(role(Role.ADMIN)), "/api/unlisted");
    }

    @Test
    void manualExpirationOperationIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/operations/expiration/run").with(role(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredParcels").value(0))
                .andExpect(jsonPath("$.expiredPickupCodes").value(0));
        assertForbidden(
                post("/api/admin/operations/expiration/run").with(role(Role.CUSTOMER)),
                "/api/admin/operations/expiration/run"
        );
        assertForbidden(
                post("/api/admin/operations/expiration/run").with(role(Role.COURIER)),
                "/api/admin/operations/expiration/run"
        );
        assertAnonymousUnauthorized(
                post("/api/admin/operations/expiration/run"),
                "/api/admin/operations/expiration/run"
        );
    }

    private void assertAnonymousUnauthorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String path
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value(path));
    }

    private void assertForbidden(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String path
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(jsonPath("$.path").value(path));
    }

    private RequestPostProcessor role(Role role) {
        return jwt()
                .jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
