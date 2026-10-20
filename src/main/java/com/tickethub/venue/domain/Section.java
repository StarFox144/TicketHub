package com.tickethub.venue.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "sections")
public class Section extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int rowsCount;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    protected Section() {
    }

    Section(Venue venue, String name, int rowsCount, int seatsPerRow) {
        if (rowsCount <= 0 || seatsPerRow <= 0) {
            throw new BusinessRuleException("Section must have at least one row and one seat per row");
        }
        this.venue = venue;
        this.name = name;
        this.rowsCount = rowsCount;
        for (int row = 1; row <= rowsCount; row++) {
            for (int number = 1; number <= seatsPerRow; number++) {
                seats.add(new Seat(this, row, number));
            }
        }
    }

    public int seatsCount() {
        return seats.size();
    }

    public Venue getVenue() {
        return venue;
    }

    public String getName() {
        return name;
    }

    public int getRowsCount() {
        return rowsCount;
    }

    public List<Seat> getSeats() {
        return Collections.unmodifiableList(seats);
    }
}
