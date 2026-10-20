package com.tickethub.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED — кілька інстансів relay не відправлять одну подію одночасно. */
    @Query(value = """
            select * from outbox
            where published_at is null
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(int limit);

    List<OutboxEvent> findByAggregateId(UUID aggregateId);
}
