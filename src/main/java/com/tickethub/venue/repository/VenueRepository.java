package com.tickethub.venue.repository;

import com.tickethub.venue.domain.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    @EntityGraph(attributePaths = "sections")
    Optional<Venue> findWithSectionsById(UUID id);

    Page<Venue> findByCityIgnoreCase(String city, Pageable pageable);
}
