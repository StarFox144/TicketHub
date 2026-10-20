package com.tickethub.venue.domain;

import com.tickethub.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class VenueTest {

    @Test
    void addSection_generatesSeatGrid() {
        var venue = new Venue("Hall", "Київ", "Street 1");

        var section = venue.addSection("Партер", 3, 4);

        assertThat(section.getSeats()).hasSize(12);
        assertThat(section.getSeats())
                .extracting(Seat::getRowNumber, Seat::getSeatNumber)
                .startsWith(tuple(1, 1), tuple(1, 2))
                .endsWith(tuple(3, 4));
        assertThat(venue.capacity()).isEqualTo(12);
    }

    @Test
    void addSection_rejectsDuplicateName() {
        var venue = new Venue("Hall", "Київ", "Street 1");
        venue.addSection("Балкон", 1, 1);

        assertThatThrownBy(() -> venue.addSection("балкон", 2, 2))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void addSection_rejectsEmptyGrid() {
        var venue = new Venue("Hall", "Київ", "Street 1");

        assertThatThrownBy(() -> venue.addSection("Партер", 0, 10))
                .isInstanceOf(BusinessRuleException.class);
    }
}
