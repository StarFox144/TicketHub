package com.tickethub.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** eventId — подія, на вході якої стоїть контролер: квиток на іншу подію не пропускаємо. */
public record ScanRequest(@NotNull UUID eventId, @NotBlank @Size(max = 64) String code) {
}
