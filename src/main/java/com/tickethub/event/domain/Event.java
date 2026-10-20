package com.tickethub.event.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import com.tickethub.user.domain.User;
import com.tickethub.venue.domain.Seat;
import com.tickethub.venue.domain.Venue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User organizer;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("price desc")
    private List<PriceZone> priceZones = new ArrayList<>();

    protected Event() {
    }

    public Event(Venue venue, User organizer, String title, String description,
                 Instant startsAt, Instant endsAt, Instant now) {
        this.venue = venue;
        this.organizer = organizer;
        this.title = title;
        this.description = description;
        reschedule(startsAt, endsAt, now);
    }

    public void update(String title, String description, Instant startsAt, Instant endsAt, Instant now) {
        requireEditable();
        this.title = title;
        this.description = description;
        if (!startsAt.equals(this.startsAt) || !endsAt.equals(this.endsAt)) {
            reschedule(startsAt, endsAt, now);
        }
    }

    public record PriceZoneSpec(String name, BigDecimal price, String currency, List<SeatRange> ranges) {
    }

    /**
     * Синхронізує цінові зони за назвою: існуючі оновлюються, нові додаються, відсутні видаляються.
     * Саме оновлення (а не clear + add) — бо Hibernate виконує INSERT раніше за DELETE,
     * і повторне додавання зони з тією ж назвою порушило б UNIQUE (event_id, name).
     */
    public void replacePriceZones(List<PriceZoneSpec> specs) {
        if (status != EventStatus.DRAFT) {
            throw new ConflictException("Price zones can be changed only for DRAFT events");
        }
        Map<String, PriceZoneSpec> byName = specs.stream()
                .collect(Collectors.toMap(PriceZoneSpec::name, Function.identity(), (a, b) -> {
                    throw new BusinessRuleException("Duplicate price zone name: " + a.name());
                }));
        requireNoOverlaps(specs);

        priceZones.removeIf(zone -> !byName.containsKey(zone.getName()));
        Set<String> existing = priceZones.stream().map(PriceZone::getName).collect(Collectors.toSet());
        priceZones.forEach(zone -> {
            var spec = byName.get(zone.getName());
            zone.update(spec.price(), spec.currency(), spec.ranges());
        });
        specs.stream()
                .filter(spec -> !existing.contains(spec.name()))
                .forEach(spec -> priceZones.add(
                        new PriceZone(this, spec.name(), spec.price(), spec.currency(), spec.ranges())));
    }

    /** Одне місце не може мати дві ціни: діапазони різних (і однієї) зон не перетинаються. */
    private static void requireNoOverlaps(List<PriceZoneSpec> specs) {
        record Owned(String zone, SeatRange range) {
        }
        List<Owned> all = specs.stream()
                .flatMap(spec -> spec.ranges().stream().map(r -> new Owned(spec.name(), r)))
                .toList();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (all.get(i).range().overlaps(all.get(j).range())) {
                    throw new BusinessRuleException("Price zones '%s' and '%s' overlap"
                            .formatted(all.get(i).zone(), all.get(j).zone()));
                }
            }
        }
    }

    /**
     * Публікація «оживляє» фізичні місця залу: кожне місце, покрите ціновою зоною, стає EventSeat.
     * Місця поза зонами на цю подію не продаються.
     */
    public List<EventSeat> publish(Collection<Seat> venueSeats, Instant now) {
        if (status != EventStatus.DRAFT) {
            throw new ConflictException("Only DRAFT event can be published, current status is " + status);
        }
        if (!startsAt.isAfter(now)) {
            throw new BusinessRuleException("Event that has already started cannot be published");
        }
        List<EventSeat> eventSeats = new ArrayList<>();
        for (Seat seat : venueSeats) {
            priceZones.stream()
                    .filter(zone -> zone.covers(seat.getSection().getId(), seat.getRowNumber()))
                    .findFirst()
                    .ifPresent(zone -> eventSeats.add(new EventSeat(this, seat, zone)));
        }
        if (eventSeats.isEmpty()) {
            throw new BusinessRuleException("Price zones do not cover any seat, nothing to sell");
        }
        status = EventStatus.PUBLISHED;
        return eventSeats;
    }

    /** Продаж відкритий лише для опублікованої події, що ще не почалася. */
    public boolean isOnSale(Instant now) {
        return status == EventStatus.PUBLISHED && startsAt.isAfter(now);
    }

    private void reschedule(Instant startsAt, Instant endsAt, Instant now) {
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessRuleException("Event must end after it starts");
        }
        if (!startsAt.isAfter(now)) {
            throw new BusinessRuleException("Event must start in the future");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    private void requireEditable() {
        if (!status.isEditable()) {
            throw new ConflictException("Event in status %s cannot be edited".formatted(status));
        }
    }

    public Venue getVenue() {
        return venue;
    }

    public User getOrganizer() {
        return organizer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public List<PriceZone> getPriceZones() {
        return Collections.unmodifiableList(priceZones);
    }
}
