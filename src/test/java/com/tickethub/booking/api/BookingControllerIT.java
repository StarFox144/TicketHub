package com.tickethub.booking.api;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.event.api.dto.SeatView;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingControllerIT extends IntegrationTestBase {

    private PublishedEvent event;
    private User buyer;

    @BeforeEach
    void setUp() {
        event = testData.publishedEvent();
        buyer = testData.user(Role.USER);
    }

    @Test
    void createOrder_reservesSeats_andFixesTotal() throws Exception {
        order(buyer, key(), event.seat(0), event.seat(5))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("UAH"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.secondsUntilExpiry", lessThanOrEqualTo(600)));

        assertThat(seatStatus(0)).isEqualTo("RESERVED");
        assertThat(seatStatus(1)).isEqualTo("AVAILABLE");
    }

    @Test
    void sameIdempotencyKey_returnsSameOrder_with200() throws Exception {
        var key = key();
        var first = orderId(order(buyer, key, event.seat(0)).andExpect(status().isCreated()));

        order(buyer, key, event.seat(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(first.toString()));
    }

    @Test
    void sameIdempotencyKey_withDifferentSeats_returns422() throws Exception {
        var key = key();
        order(buyer, key, event.seat(0)).andExpect(status().isCreated());

        order(buyer, key, event.seat(1)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(event.eventId(), List.of(event.seat(0))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void takenSeat_returns409_andNothingIsReserved() throws Exception {
        order(testData.user(Role.USER), key(), event.seat(2)).andExpect(status().isCreated());

        order(buyer, key(), event.seat(1), event.seat(2), event.seat(3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.unavailableSeatIds", hasSize(1)))
                .andExpect(jsonPath("$.unavailableSeatIds[0]").value(event.seat(2).toString()));

        // все або нічого: сусідні вільні місця не зарезервувались
        assertThat(seatStatus(1)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(3)).isEqualTo("AVAILABLE");
    }

    @Test
    void seatOfAnotherEvent_returns404() throws Exception {
        var other = testData.publishedEvent();

        order(buyer, key(), event.seat(0), other.seat(0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.missingSeatIds[0]").value(other.seat(0).toString()));
    }

    @Test
    void tooManySeats_returns422() throws Exception {
        var eleven = new ArrayList<>(event.seats().stream().map(SeatView::id).toList());
        eleven.add(UUID.randomUUID());

        order(buyer, key(), eleven.toArray(UUID[]::new)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void organizer_cannotBuy() throws Exception {
        order(event.organizer(), key(), event.seat(0)).andExpect(status().isForbidden());
    }

    @Test
    void cancel_releasesSeats_andSeatCanBeBoughtAgain() throws Exception {
        var orderId = orderId(order(buyer, key(), event.seat(0)).andExpect(status().isCreated()));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.secondsUntilExpiry").value(0));
        assertThat(seatStatus(0)).isEqualTo("AVAILABLE");

        // частковий унікальний індекс не заважає: стара позиція вже неактивна
        order(testData.user(Role.USER), key(), event.seat(0)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isConflict());
    }

    @Test
    void foreignOrder_isInvisible() throws Exception {
        var orderId = orderId(order(buyer, key(), event.seat(0)).andExpect(status().isCreated()));
        var stranger = testData.bearer(Role.USER);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void myOrders_listsOnlyOwnOrders() throws Exception {
        var first = orderId(order(buyer, key(), event.seat(0)).andExpect(status().isCreated()));
        var second = orderId(order(buyer, key(), event.seat(1)).andExpect(status().isCreated()));
        order(testData.user(Role.USER), key(), event.seat(2)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].orderId", containsInAnyOrder(first.toString(), second.toString())));
    }

    private ResultActions order(User user, String key, UUID... seatIds) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, testData.bearer(user))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateOrderRequest(event.eventId(), List.of(seatIds)))));
    }

    private String seatStatus(int index) throws Exception {
        var seatId = event.seat(index).toString();
        var body = mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var seat : objectMapper.readTree(body)) {
            if (seat.get("id").asText().equals(seatId)) {
                return seat.get("status").asText();
            }
        }
        throw new AssertionError("Seat %s is missing from the seat map".formatted(seatId));
    }

    private UUID orderId(ResultActions actions) throws Exception {
        var body = actions.andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("orderId").asText());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
