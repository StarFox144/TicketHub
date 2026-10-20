package com.tickethub.event.service;

import com.tickethub.common.exception.NotFoundException;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.event.repository.EventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Перевірка власника для {@code @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")}.
 * Для неіснуючої події кидає NotFoundException (SpEL пробрасує його як є) — клієнт отримує 404, а не 403.
 */
@Component("eventSecurity")
public class EventSecurity {

    private final EventRepository eventRepository;

    public EventSecurity(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID eventId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return false;
        }
        var organizerId = eventRepository.findOrganizerIdById(eventId)
                .orElseThrow(() -> new NotFoundException("Event", eventId));
        return organizerId.equals(user.id());
    }
}
