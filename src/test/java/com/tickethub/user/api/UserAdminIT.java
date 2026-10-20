package com.tickethub.user.api;

import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData;
import com.tickethub.user.api.dto.ChangeRoleRequest;
import com.tickethub.user.api.dto.LoginRequest;
import com.tickethub.user.api.dto.RefreshRequest;
import com.tickethub.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminIT extends IntegrationTestBase {

    @Test
    void admin_canPromoteUserToOrganizer() throws Exception {
        var user = testData.user(Role.USER);

        mockMvc.perform(put("/api/v1/users/{id}/role", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangeRoleRequest(Role.ORGANIZER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORGANIZER"));
    }

    @Test
    void nonAdmin_cannotChangeRoles_orListUsers() throws Exception {
        var user = testData.user(Role.USER);
        var organizerToken = testData.bearer(Role.ORGANIZER);

        mockMvc.perform(put("/api/v1/users/{id}/role", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangeRoleRequest(Role.ADMIN))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, organizerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUser_cannotLogin_norRefresh_untilUnblocked() throws Exception {
        var user = testData.user(Role.USER);
        var adminToken = testData.bearer(Role.ADMIN);
        var login = json(new LoginRequest(user.getEmail(), TestData.PASSWORD));
        var tokens = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/users/{id}/block", user.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Account is blocked"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(tokens.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/users/{id}/unblock", user.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk());
    }

    @Test
    void admin_cannotBlockThemselves() throws Exception {
        var admin = testData.user(Role.ADMIN);

        mockMvc.perform(post("/api/v1/users/{id}/block", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(admin)))
                .andExpect(status().isUnprocessableEntity());
    }
}
