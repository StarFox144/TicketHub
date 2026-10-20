package com.tickethub.support;

import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import com.tickethub.venue.api.dto.CreateSectionRequest;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.service.VenueService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Фабрика тестових даних. Тестам дозволено ходити в репозиторії напряму. */
@Component
public class TestData {

    private final UserRepository userRepository;
    private final VenueService venueService;

    public TestData(UserRepository userRepository, VenueService venueService) {
        this.userRepository = userRepository;
        this.venueService = venueService;
    }

    public User user(Role role) {
        var email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        return userRepository.save(new User(email, "{noop}secret", "Test " + role, role));
    }

    public VenueView venue(String city) {
        return venueService.create(new CreateVenueRequest("Test Hall " + UUID.randomUUID(), city, "Test street 1",
                List.of(new CreateSectionRequest("Партер", 2, 5))));
    }
}
