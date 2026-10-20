package com.tickethub.venue.repository;

import com.tickethub.venue.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    interface SectionSeatCount {
        UUID getSectionId();

        long getSeatsCount();
    }

    @Query("""
            select s.section.id as sectionId, count(s) as seatsCount
            from Seat s
            where s.section.venue.id = :venueId
            group by s.section.id
            """)
    List<SectionSeatCount> countBySectionForVenue(UUID venueId);
}
