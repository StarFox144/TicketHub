package com.tickethub.event.repository;

import com.tickethub.event.domain.Event;
import com.tickethub.event.domain.EventStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/** Фільтри каталогу подій. Кожен метод повертає null, якщо параметр не задано — Spring Data його пропускає. */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> hasStatus(EventStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Event> inCity(String city) {
        if (city == null || city.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("venue").get("city")), city.toLowerCase(Locale.ROOT));
    }

    public static Specification<Event> startsFrom(Instant from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    public static Specification<Event> startsBefore(Instant to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThan(root.get("startsAt"), to);
    }

    public static Specification<Event> titleContains(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
