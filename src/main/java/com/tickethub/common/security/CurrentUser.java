package com.tickethub.common.security;

import java.util.UUID;

/**
 * Principal автентифікованого запиту, відновлений з access-токена без звернення до БД.
 * Роль — рядок, щоб common не залежав від модуля user.
 */
public record CurrentUser(UUID id, String email, String role) {

    public boolean hasRole(String expected) {
        return role.equals(expected);
    }
}
