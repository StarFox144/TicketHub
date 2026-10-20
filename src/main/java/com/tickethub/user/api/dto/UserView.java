package com.tickethub.user.api.dto;

import com.tickethub.user.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record UserView(UUID id, String email, String fullName, Role role, boolean enabled, Instant createdAt) {
}
