package com.tickethub.user.api.dto;

import com.tickethub.user.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {
}
