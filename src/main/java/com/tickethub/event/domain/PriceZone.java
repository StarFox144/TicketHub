package com.tickethub.event.domain;

import com.tickethub.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

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

    protected PriceZone() {
    }

    PriceZone(Event event, String name, BigDecimal price, String currency) {
        this.event = event;
        this.name = name;
        this.price = price;
        this.currency = currency;
    }

    void reprice(BigDecimal price, String currency) {
        this.price = price;
        this.currency = currency;
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
}
