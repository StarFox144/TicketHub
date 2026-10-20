package com.tickethub.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Самостійна реєстрація завжди створює роль USER; ORGANIZER/ADMIN призначає адміністратор. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt враховує лише перші 72 байти пароля
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 255) String fullName
) {
}
