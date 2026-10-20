package com.tickethub.event.domain;

import com.tickethub.common.audit.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "price_zones")
public class PriceZone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @ElementCollection
    @CollectionTable(name = "price_zone_ranges", joinColumns = @JoinColumn(name = "price_zone_id"))
    private List<SeatRange> ranges = new ArrayList<>();

    protected PriceZone() {
    }

    PriceZone(Event event, String name, BigDecimal price, String currency, List<SeatRange> ranges) {
        this.event = event;
        this.name = name;
        update(price, currency, ranges);
    }

    void update(BigDecimal price, String currency, List<SeatRange> ranges) {
        this.price = price;
        this.currency = currency;
        this.ranges.clear();
        this.ranges.addAll(ranges);
    }

    public boolean covers(UUID sectionId, int row) {
        return ranges.stream().anyMatch(r -> r.covers(sectionId, row));
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public List<SeatRange> getRanges() {
        return Collections.unmodifiableList(ranges);
    }
}
