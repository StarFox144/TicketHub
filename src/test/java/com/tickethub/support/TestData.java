package com.tickethub.support;

import com.tickethub.common.security.JwtService;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import com.tickethub.venue.api.dto.CreateSectionRequest;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.service.VenueService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Фабрика тестових даних. Тестам дозволено ходити в репозиторії напряму. */
@Component
public class TestData {

    public static final String PASSWORD = "password123";

    private final UserRepository userRepository;
    private final VenueService venueService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TestData(UserRepository userRepository, VenueService venueService,
                    PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.venueService = venueService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User user(Role role) {
        var email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        return userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), "Test " + role, role));
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
}
