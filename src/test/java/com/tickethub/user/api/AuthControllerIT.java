package com.tickethub.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData;
import com.tickethub.user.api.dto.LoginRequest;
import com.tickethub.user.api.dto.RefreshRequest;
import com.tickethub.user.api.dto.RegisterRequest;
import com.tickethub.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTestBase {

    @Test
    void register_returnsTokens_andAccessTokenOpensProfile() throws Exception {
        var email = "New.User-" + UUID.randomUUID() + "@Test.local";

        var tokens = perform("/api/v1/auth/register", new RegisterRequest(email, "s3cret-pass", "Іван Петренко"), 201);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.fullName").value("Іван Петренко"));
    }

    @Test
    void register_duplicateEmail_caseInsensitive_returns409() throws Exception {
        var email = "dup-" + UUID.randomUUID() + "@test.local";
        perform("/api/v1/auth/register", new RegisterRequest(email, "s3cret-pass", "A"), 201);

        perform("/api/v1/auth/register", new RegisterRequest(email.toUpperCase(), "s3cret-pass", "B"), 409);
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        perform("/api/v1/auth/register", new RegisterRequest("short-" + UUID.randomUUID() + "@test.local", "123", "A"), 400);
    }

    @Test
    void login_withValidCredentials_returnsTokenPair() throws Exception {
        var user = testData.user(Role.ORGANIZER);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(user.getEmail(), TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())));
    }

    @Test
    void login_wrongPassword_andUnknownEmail_lookTheSame() throws Exception {
        var user = testData.user(Role.USER);

        var wrongPassword = perform("/api/v1/auth/login", new LoginRequest(user.getEmail(), "wrong-password"), 401);
        var unknownEmail = perform("/api/v1/auth/login", new LoginRequest("nobody@test.local", "wrong-password"), 401);

        assertThat(wrongPassword.get("detail"))
                .isEqualTo(unknownEmail.get("detail"));
    }

    @Test
    void refresh_rotatesToken_andOldTokenCannotBeReused() throws Exception {
        var user = testData.user(Role.USER);
        var first = perform("/api/v1/auth/login", new LoginRequest(user.getEmail(), TestData.PASSWORD), 200);
        var firstRefresh = first.get("refreshToken").asText();

        var second = perform("/api/v1/auth/refresh", new RefreshRequest(firstRefresh), 200);
        var secondRefresh = second.get("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // повторне використання погашеного токена — ознака крадіжки: 401 і відкликання всіх сесій
        perform("/api/v1/auth/refresh", new RefreshRequest(firstRefresh), 401);
        perform("/api/v1/auth/refresh", new RefreshRequest(secondRefresh), 401);
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        var user = testData.user(Role.USER);
        var tokens = perform("/api/v1/auth/login", new LoginRequest(user.getEmail(), TestData.PASSWORD), 200);
        var refresh = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RefreshRequest(refresh))))
                .andExpect(status().isNoContent());

        perform("/api/v1/auth/refresh", new RefreshRequest(refresh), 401);
    }

    @Test
    void refresh_withGarbageToken_returns401() throws Exception {
        perform("/api/v1/auth/refresh", new RefreshRequest("not-a-real-token"), 401);
    }

    @Test
    void profile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode perform(String url, Object body, int expectedStatus) throws Exception {
        var response = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
