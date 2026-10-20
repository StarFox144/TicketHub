package com.tickethub.venue.api;

import com.tickethub.support.IntegrationTestBase;
import com.tickethub.venue.api.dto.CreateSectionRequest;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VenueControllerIT extends IntegrationTestBase {

    @Test
    void createVenue_generatesSeats_andCanBeFetched() throws Exception {
        var request = new CreateVenueRequest("Опера", "Львів", "пр. Свободи, 28", List.of(
                new CreateSectionRequest("Партер", 10, 15),
                new CreateSectionRequest("Балкон", 5, 10)));

        var response = mockMvc.perform(post("/api/v1/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/venues/")))
                .andExpect(jsonPath("$.capacity").value(200))
                .andExpect(jsonPath("$.sections", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        var id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/venues/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Опера"))
                .andExpect(jsonPath("$.capacity").value(200))
                .andExpect(jsonPath("$.sections[0].name").value("Балкон"))
                .andExpect(jsonPath("$.sections[0].seatsCount").value(50));
    }

    @Test
    void createVenue_withInvalidBody_returnsProblemDetail() throws Exception {
        var request = new CreateVenueRequest("", "Київ", "addr", List.of());

        mockMvc.perform(post("/api/v1/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.sections").exists());
    }

    @Test
    void getVenue_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/venues/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
