package com.tickethub.booking.repository;

import com.tickethub.booking.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    /** Скасування блокує замовлення, щоб не перетнутися з ExpiredReservationJob (той його пропустить). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BookingOrder o where o.id = :id")
    Optional<Order> findForUpdateById(UUID id);

    /**
     * SKIP LOCKED: кілька інстансів застосунку можуть запускати job одночасно —
     * кожен візьме свою порцію, і жодне замовлення не обробиться двічі. ShedLock не потрібен.
     */
    @Query(value = """
            select * from orders
            where status = 'PENDING' and expires_at <= :now
            order by expires_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Order> lockExpired(Instant now, int limit);
}
