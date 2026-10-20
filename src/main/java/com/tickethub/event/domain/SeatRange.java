package com.tickethub.event.domain;

import com.tickethub.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/** Діапазон рядів секції, що належить ціновій зоні. null у межі означає «з першого» / «до останнього» ряду. */
@Embeddable
public record SeatRange(
        @Column(name = "section_id", nullable = false) UUID sectionId,
        @Column(name = "row_from") Integer rowFrom,
        @Column(name = "row_to") Integer rowTo
) {

    public SeatRange {
        if (sectionId == null) {
            throw new BusinessRuleException("Seat range must reference a section");
        }
        if ((rowFrom != null && rowFrom < 1) || (rowTo != null && rowTo < 1)) {
            throw new BusinessRuleException("Row numbers start from 1");
        }
        if (rowFrom != null && rowTo != null && rowTo < rowFrom) {
            throw new BusinessRuleException("rowTo must not be less than rowFrom");
        }
    }

    public boolean covers(UUID section, int row) {
        return sectionId.equals(section) && firstRow() <= row && row <= lastRow();
    }

    public boolean overlaps(SeatRange other) {
        return sectionId.equals(other.sectionId) && firstRow() <= other.lastRow() && other.firstRow() <= lastRow();
    }

    private int firstRow() {
        return rowFrom == null ? 1 : rowFrom;
    }

    private int lastRow() {
        return rowTo == null ? Integer.MAX_VALUE : rowTo;
    }
}
