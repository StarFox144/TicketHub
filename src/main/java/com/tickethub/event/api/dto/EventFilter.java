package com.tickethub.event.api.dto;

import java.time.Instant;

/** Параметри каталогу: ?city=Київ&from=2026-11-01T00:00:00Z&to=...&q=джаз */
public record EventFilter(String city, Instant from, Instant to, String q) {
}
