package com.tickethub.event.service;

import com.tickethub.event.api.dto.SeatView;
import com.tickethub.event.repository.EventSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Карта місць — найгарячіший запит під час ажіотажу: її оновлюють тисячі людей.
 * Кешуємо на кілька секунд і скидаємо при кожній зміні статусів місць події.
 * Можлива затримка — не більше TTL; продаж від цього не страждає, бо резерв перевіряє БД.
 */
@Service
public class SeatMapService {

    public static final String CACHE = "seat-map";

    private static final Logger log = LoggerFactory.getLogger(SeatMapService.class);

    private final EventSeatRepository eventSeatRepository;
    private final CacheManager cacheManager;

    public SeatMapService(EventSeatRepository eventSeatRepository, CacheManager cacheManager) {
        this.eventSeatRepository = eventSeatRepository;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CACHE, key = "#eventId")
    @Transactional(readOnly = true)
    public List<SeatView> seatMap(UUID eventId) {
        return eventSeatRepository.findSeatMap(eventId);
    }

    /**
     * Скидання — після коміту: якщо скинути раніше, читач між evict і комітом
     * покладе в кеш ще старі статуси. Збій Redis тут лише логуємо: зміна в БД уже закомічена,
     * а застарілий запис сам зникне за TTL.
     */
    public void evictAfterCommit(UUID eventId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictQuietly(eventId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictQuietly(eventId);
            }
        });
    }

    private void evictQuietly(UUID eventId) {
        try {
            var cache = cacheManager.getCache(CACHE);
            if (cache != null) {
                cache.evict(eventId);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to evict seat map of event {}, it will expire by TTL: {}", eventId, e.getMessage());
        }
    }
}
