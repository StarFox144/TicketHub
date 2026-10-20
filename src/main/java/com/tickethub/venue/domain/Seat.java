package com.tickethub.venue.domain;

import com.tickethub.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Фізичне місце в залі. Статус і ціна на конкретну подію живуть в EventSeat. */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"section_id", "row_number", "seat_number"}))
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Section section;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    protected Seat() {
    }

    Seat(Section section, int rowNumber, int seatNumber) {
        this.section = section;
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
    }

    public Section getSection() {
        return section;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}
