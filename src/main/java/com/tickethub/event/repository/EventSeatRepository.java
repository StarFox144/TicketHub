package com.tickethub.event.repository;

import com.tickethub.event.api.dto.SeatView;
import com.tickethub.event.domain.EventSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    /**
     * Песимістичне блокування: SELECT ... FOR NO KEY UPDATE на вибрані рядки.
     * Порядок за id — щоб два запити з перехресними наборами місць брали блокування
     * в однаковому порядку і не потрапляли в deadlock.
     * Цінову зону навмисно не join fetch: інакше FOR UPDATE заблокував би й рядок price_zones,
     * і всі покупки в одній зоні йшли б строго по черзі.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from EventSeat s
            where s.event.id = :eventId and s.id in :ids
            order by s.id
            """)
    List<EventSeat> lockForReservation(UUID eventId, Collection<UUID> ids);

    /** Оптимістичний варіант: без блокувань, конфлікт виявить @Version при UPDATE. */
    @Query("""
            select s from EventSeat s join fetch s.priceZone
            where s.event.id = :eventId and s.id in :ids
            order by s.id
            """)
    List<EventSeat> findForReservation(UUID eventId, Collection<UUID> ids);

    /** Блокування набору місць за id (продаж після оплати); порядок за id — як і при резерві. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSeat s where s.id in :ids order by s.id")
    List<EventSeat> lockByIds(Collection<UUID> ids);

    @Query("""
            select new com.tickethub.event.api.dto.SeatView(
                es.id, sec.name, s.rowNumber, s.seatNumber, z.name, z.price, z.currency, es.status)
            from EventSeat es
            join es.seat s
            join s.section sec
            join es.priceZone z
            where es.id in :ids
            """)
    List<SeatView> findSeatViews(Collection<UUID> ids);

    @Query("""
            select new com.tickethub.event.api.dto.SeatView(
                es.id, sec.name, s.rowNumber, s.seatNumber, z.name, z.price, z.currency, es.status)
            from EventSeat es
            join es.seat s
            join s.section sec
            join es.priceZone z
            where es.event.id = :eventId
            order by sec.name, s.rowNumber, s.seatNumber
            """)
    List<SeatView> findSeatMap(UUID eventId);
}
