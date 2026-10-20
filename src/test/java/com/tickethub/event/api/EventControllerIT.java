package com.tickethub.event.api;

import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.SeatRangeDto;
import com.tickethub.event.api.dto.UpdateEventRequest;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerIT extends IntegrationTestBase {

    private static final Instant STARTS = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant ENDS = STARTS.plus(Duration.ofHours(2));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createEvent_organizerComesFromToken() throws Exception {
        var organizer = testData.user(Role.ORGANIZER);
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), "Рок-концерт", "Опис", STARTS, ENDS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.venue.id").value(venue.id().toString()))
                .andExpect(jsonPath("$.organizerId").value(organizer.getId().toString()));
    }

    @Test
    void createEvent_byRegularUser_returns403() throws Exception {
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), "Концерт", null, STARTS, ENDS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEvent_anonymous_returns401() throws Exception {
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), "Концерт", null, STARTS, ENDS))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createEvent_endingBeforeStart_returns422() throws Exception {
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), "Концерт", null, ENDS, STARTS))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Event must end after it starts"));
    }

    @Test
    void owner_canUpdateEvent_andSetPriceZones() throws Exception {
        var owner = testData.user(Role.ORGANIZER);
        var id = createEvent(owner, "Київ", "Стендап");

        mockMvc.perform(put("/api/v1/events/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEventRequest("Стендап вечір", "Новий опис", STARTS, ENDS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Стендап вечір"));

        var section = sectionOf(id);
        var zones = new PriceZonesRequest(List.of(
                new PriceZonesRequest.Zone("VIP", new BigDecimal("1200.00"), null,
                        List.of(new SeatRangeDto(section, 1, 1))),
                new PriceZonesRequest.Zone("Стандарт", new BigDecimal("600.00"), "UAH",
                        List.of(new SeatRangeDto(section, 2, null)))));
        mockMvc.perform(post("/api/v1/events/{id}/price-zones", id)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(zones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceZones", hasSize(2)));

        // повторний виклик з тією ж назвою зони не повинен падати на UNIQUE (event_id, name)
        var updated = new PriceZonesRequest(List.of(new PriceZonesRequest.Zone("VIP", new BigDecimal("1500.00"), null,
                List.of(SeatRangeDto.wholeSection(section)))));
        mockMvc.perform(post("/api/v1/events/{id}/price-zones", id)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updated)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/{id}", id).header(HttpHeaders.AUTHORIZATION, testData.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceZones", hasSize(1)))
                .andExpect(jsonPath("$.priceZones[0].price").value(1500.00))
                .andExpect(jsonPath("$.priceZones[0].currency").value("UAH"))
                .andExpect(jsonPath("$.priceZones[0].ranges[0].sectionId").value(section.toString()));
    }

    @Test
    void anotherOrganizer_cannotUpdateEvent() throws Exception {
        var id = createEvent(testData.user(Role.ORGANIZER), "Київ", "Чужа подія");

        mockMvc.perform(put("/api/v1/events/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEventRequest("Захоплено", null, STARTS, ENDS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUnknownEvent_returns404_not403() throws Exception {
        mockMvc.perform(put("/api/v1/events/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEventRequest("Назва", null, STARTS, ENDS))))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftEvent_isHiddenFromPublicAndOtherUsers_butVisibleToAdmin() throws Exception {
        var id = createEvent(testData.user(Role.ORGANIZER), "Київ", "Секретна чернетка");

        mockMvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{id}", id).header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.USER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/{id}", id).header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ADMIN)))
                .andExpect(status().isOk());

        jdbc.update("update events set status = 'PUBLISHED' where id = ?", id);
        mockMvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void catalog_showsOnlyPublishedEvents_filteredByCityAndQuery() throws Exception {
        var city = "Місто-" + UUID.randomUUID();
        var organizer = testData.user(Role.ORGANIZER);
        var published = createEvent(organizer, city, "Джаз у парку");
        createEvent(organizer, city, "Джаз чернетка");
        var opera = createEvent(organizer, city, "Опера");
        // публікація з'явиться на етапі 3, поки що переводимо статус напряму
        jdbc.update("update events set status = 'PUBLISHED' where id in (?, ?)", published, opera);

        mockMvc.perform(get("/api/v1/events").param("city", city).param("q", "джаз"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(published.toString()))
                .andExpect(jsonPath("$.content[0].city").value(city))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/events").param("city", city))
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/events").param("city", city).param("from", ENDS.toString()))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private UUID sectionOf(UUID eventId) {
        return jdbc.queryForObject("select s.id from sections s join events e on e.venue_id = s.venue_id "
                + "where e.id = ? limit 1", UUID.class, eventId);
    }

    private UUID createEvent(User organizer, String city, String title) throws Exception {
        var venue = testData.venue(city);
        var response = mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), title, null, STARTS, ENDS))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
