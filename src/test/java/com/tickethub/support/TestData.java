package com.tickethub.support;

import com.tickethub.common.security.JwtService;
import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.SeatRangeDto;
import com.tickethub.event.api.dto.SeatView;
import com.tickethub.event.service.EventService;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import com.tickethub.venue.api.dto.CreateSectionRequest;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.service.VenueService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Фабрика тестових даних. Тестам дозволено ходити в репозиторії напряму. */
@Component
public class TestData {

    public static final String PASSWORD = "password123";

    private final UserRepository userRepository;
    private final VenueService venueService;
    private final EventService eventService;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    /** BCrypt навмисно повільний — рахуємо хеш один раз, а не для кожного з 50 користувачів. */
    private final String passwordHash;

    public TestData(UserRepository userRepository, VenueService venueService, EventService eventService,
                    PasswordEncoder passwordEncoder, JwtService jwtService, StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.venueService = venueService;
        this.eventService = eventService;
        this.jwtService = jwtService;
        this.redis = redis;
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    public User user(Role role) {
        var email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        return userRepository.save(new User(email, passwordHash, "Test " + role, role));
    }

    /** Значення заголовка Authorization для користувача. */
    public String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user.toCurrentUser()).value();
    }

    public String bearer(Role role) {
        return bearer(user(role));
    }

    public VenueView venue(String city) {
        return venueService.create(new CreateVenueRequest("Test Hall " + UUID.randomUUID(), city, "Test street 1",
                List.of(new CreateSectionRequest("Партер", 2, 5))));
    }

    /** Імітує протухання ключа місця в Redis (SET seat:{id} ... EX 600). */
    public void forgetSeatClaim(UUID eventSeatId) {
        redis.delete("seat:" + eventSeatId);
    }

    public record PublishedEvent(UUID eventId, User organizer, List<SeatView> seats) {

        public UUID seat(int index) {
            return seats.get(index).id();
        }
    }

    /** Опублікована подія на 10 місць (2 ряди × 5): ряд 1 — VIP по 1000, ряд 2 — Стандарт по 500. */
    public PublishedEvent publishedEvent() {
        var organizer = user(Role.ORGANIZER);
        var venue = venue("Київ");
        var section = venue.sections().getFirst().id();
        var startsAt = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
        var event = eventService.create(organizer.getId(), new CreateEventRequest(
                venue.id(), "Подія " + UUID.randomUUID(), null, startsAt, startsAt.plus(Duration.ofHours(2))));
        eventService.setPriceZones(event.id(), new PriceZonesRequest(List.of(
                new PriceZonesRequest.Zone("VIP", new BigDecimal("1000.00"), "UAH",
                        List.of(new SeatRangeDto(section, 1, 1))),
                new PriceZonesRequest.Zone("Стандарт", new BigDecimal("500.00"), "UAH",
                        List.of(new SeatRangeDto(section, 2, 2))))));
        eventService.publish(event.id());
        return new PublishedEvent(event.id(), organizer, eventService.seatMap(event.id(), null));
    }
}
