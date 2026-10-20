package com.tickethub.event.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Повний набір цінових зон події разом із місцями, що до них входять. Приклад:
 * VIP — ряди 1–3 партеру, Стандарт — решта партеру, Балкон — уся секція (rowFrom/rowTo не задані).
 */
public record PriceZonesRequest(@NotEmpty @Size(max = 20) List<@Valid Zone> zones) {

    public record Zone(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotEmpty @Size(max = 50) List<@Valid @NotNull SeatRangeDto> seats
    ) {
        public String currencyOrDefault() {
            return currency == null ? "UAH" : currency;
        }
    }
}
