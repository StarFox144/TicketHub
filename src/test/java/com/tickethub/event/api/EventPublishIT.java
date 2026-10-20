package com.tickethub.event.api;

import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.SeatRangeDto;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.venue.api.dto.CreateSectionRequest;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.service.VenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventPublishIT extends IntegrationTestBase {

    private static final Instant STARTS = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private VenueService venueService;

    private User owner;
    private VenueView venue;
    private UUID stalls;
    private UUID balcony;
    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        owner = testData.user(Role.ORGANIZER);
        venue = venueService.create(new CreateVenueRequest("Hall " + UUID.randomUUID(), "Київ", "addr", List.of(
                new CreateSectionRequest("Балкон", 2, 5),
                new CreateSectionRequest("Партер", 4, 5))));
        balcony = venue.sections().get(0).id();
        stalls = venue.sections().get(1).id();

        var response = mockMvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest(venue.id(), "Концерт", null,
                                STARTS, STARTS.plus(Duration.ofHours(2))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        eventId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    void publish_createsSeatMapFromPriceZones() throws Exception {
        setZones(new PriceZonesRequest(List.of(
                new PriceZonesRequest.Zone("VIP", new BigDecimal("1200.00"), "UAH",
                        List.of(new SeatRangeDto(stalls, 1, 2))),
                new PriceZonesRequest.Zone("Стандарт", new BigDecimal("600.00"), "UAH",
                        List.of(new SeatRangeDto(stalls, 3, 4))))))
                .andExpect(status().isOk());

        // до публікації карти місць немає — чернетка прихована від публіки
        mockMvc.perform(get("/api/v1/events/{id}/seats", eventId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // балкон не покритий жодною зоною — на цю подію не продається
        mockMvc.perform(get("/api/v1/events/{id}/seats", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[*].status", everyItem(is("AVAILABLE"))))
                .andExpect(jsonPath("$[0].section").value("Партер"))
                .andExpect(jsonPath("$[0].row").value(1))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].zone").value("VIP"))
                .andExpect(jsonPath("$[0].price").value(1200.00))
                .andExpect(jsonPath("$[19].zone").value("Стандарт"));
    }

    @Test
    void publish_twice_returns409() throws Exception {
        setZones(new PriceZonesRequest(List.of(new PriceZonesRequest.Zone("Усі", BigDecimal.TEN, "UAH",
                List.of(SeatRangeDto.wholeSection(balcony))))));
        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_withoutZones_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void publish_byAnotherOrganizer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ORGANIZER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void overlappingZones_return422() throws Exception {
        setZones(new PriceZonesRequest(List.of(
                new PriceZonesRequest.Zone("VIP", BigDecimal.TEN, "UAH", List.of(new SeatRangeDto(stalls, 1, 2))),
                new PriceZonesRequest.Zone("Стандарт", BigDecimal.ONE, "UAH", List.of(SeatRangeDto.wholeSection(stalls))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Price zones 'VIP' and 'Стандарт' overlap"));
    }

    @Test
    void sectionOfAnotherVenue_returns422() throws Exception {
        var foreignSection = testData.venue("Львів").sections().getFirst().id();

        setZones(new PriceZonesRequest(List.of(new PriceZonesRequest.Zone("VIP", BigDecimal.TEN, "UAH",
                List.of(SeatRangeDto.wholeSection(foreignSection))))))
                .andExpect(status().isUnprocessableEntity());
    }

    private ResultActions setZones(PriceZonesRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{id}/price-zones", eventId)
                .header(HttpHeaders.AUTHORIZATION, testData.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }
}
