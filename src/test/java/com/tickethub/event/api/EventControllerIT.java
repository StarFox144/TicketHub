package com.tickethub.event.api;

import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.UpdateEventRequest;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData;
import com.tickethub.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TestData testData;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createEvent_startsAsDraft() throws Exception {
        var organizer = testData.user(Role.ORGANIZER);
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), organizer.getId(), "Рок-концерт",
                                "Опис", STARTS, ENDS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.venue.id").value(venue.id().toString()))
                .andExpect(jsonPath("$.organizerId").value(organizer.getId().toString()));
    }

    @Test
    void createEvent_byRegularUser_isRejected() throws Exception {
        var user = testData.user(Role.USER);
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), user.getId(), "Концерт", null, STARTS, ENDS))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createEvent_endingBeforeStart_returns422() throws Exception {
        var organizer = testData.user(Role.ORGANIZER);
        var venue = testData.venue("Київ");

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), organizer.getId(), "Концерт", null,
                                ENDS, STARTS))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Event must end after it starts"));
    }

    @Test
    void updateEvent_andSetPriceZones() throws Exception {
        var id = createEvent("Київ", "Стендап");

        mockMvc.perform(put("/api/v1/events/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateEventRequest("Стендап вечір", "Новий опис", STARTS, ENDS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Стендап вечір"));

        var zones = new PriceZonesRequest(List.of(
                new PriceZonesRequest.Zone("VIP", new BigDecimal("1200.00"), null),
                new PriceZonesRequest.Zone("Стандарт", new BigDecimal("600.00"), "UAH")));
        mockMvc.perform(post("/api/v1/events/{id}/price-zones", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(zones)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceZones", hasSize(2)));

        // повторний виклик з тією ж назвою зони не повинен падати на UNIQUE (event_id, name)
        var updated = new PriceZonesRequest(List.of(new PriceZonesRequest.Zone("VIP", new BigDecimal("1500.00"), null)));
        mockMvc.perform(post("/api/v1/events/{id}/price-zones", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updated)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceZones", hasSize(1)))
                .andExpect(jsonPath("$.priceZones[0].price").value(1500.00))
                .andExpect(jsonPath("$.priceZones[0].currency").value("UAH"));
    }

    @Test
    void catalog_showsOnlyPublishedEvents_filteredByCityAndQuery() throws Exception {
        var city = "Місто-" + UUID.randomUUID();
        var published = createEvent(city, "Джаз у парку");
        createEvent(city, "Джаз чернетка");
        createEvent(city, "Опера");
        // публікація з'явиться на етапі 3, поки що переводимо статус напряму
        jdbc.update("update events set status = 'PUBLISHED' where id = ?", published);
        jdbc.update("update events set status = 'PUBLISHED' where title = 'Опера' and venue_id in "
                + "(select id from venues where city = ?)", city);

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

    private UUID createEvent(String city, String title) throws Exception {
        var organizer = testData.user(Role.ORGANIZER);
        var venue = testData.venue(city);
        var response = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), organizer.getId(), title, null, STARTS, ENDS))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
