package com.tickethub.ticket.service;

import com.tickethub.booking.service.OrderPayments.PaidItem;
import com.tickethub.booking.service.OrderPayments.PaidOrder;
import com.tickethub.booking.service.OrderService;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.event.service.EventService;
import com.tickethub.event.service.TicketEventInfo;
import com.tickethub.ticket.api.dto.ScanResult;
import com.tickethub.ticket.api.dto.TicketView;
import com.tickethub.ticket.domain.Ticket;
import com.tickethub.ticket.repository.TicketRepository;
import com.tickethub.ticket.service.PdfTicketGenerator.TicketContent;
import com.tickethub.ticket.service.TicketEmailSender.Attachment;
import com.tickethub.ticket.service.TicketEmailSender.TicketEmail;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final OrderService orderService;
    private final EventService eventService;
    private final UserService userService;
    private final PdfTicketGenerator pdfGenerator;
    private final TicketStorage storage;
    private final TicketEmailSender emailSender;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final DateTimeFormatter dateFormat;

    public TicketService(TicketRepository ticketRepository, OrderService orderService, EventService eventService,
                         UserService userService, PdfTicketGenerator pdfGenerator, TicketStorage storage,
                         TicketEmailSender emailSender, Clock clock, PlatformTransactionManager transactionManager,
                         @Value("${tickethub.tickets.display-zone}") ZoneId displayZone) {
        this.ticketRepository = ticketRepository;
        this.orderService = orderService;
        this.eventService = eventService;
        this.userService = userService;
        this.pdfGenerator = pdfGenerator;
        this.storage = storage;
        this.emailSender = emailSender;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("uk"))
                .withZone(displayZone);
    }

    /**
     * Обробник події order.paid. Ідемпотентний — RabbitMQ доставляє at-least-once:
     * повтор не створює других квитків і не перегенеровує вже збережені PDF.
     * Квитки комітяться до відправки листа: якщо пошта впаде, ретрай лише надішле лист ще раз.
     */
    public void issueAndSend(UUID orderId) {
        var email = tx.execute(status -> issue(orderId));
        if (email != null) {
            emailSender.send(email);
            log.info("Tickets for order {} sent to {}", orderId, email.to());
        }
    }

    private TicketEmail issue(UUID orderId) {
        var order = orderService.findPaidOrder(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} is not paid, tickets are not issued", orderId);
            return null;
        }
        var tickets = ensureTickets(order);
        var event = eventService.ticketInfo(order.eventId(), tickets.stream().map(Ticket::getEventSeatId).toList());
        var holder = userService.getById(order.userId());

        List<TicketEmailSender.Seat> seats = new ArrayList<>();
        List<Attachment> attachments = new ArrayList<>();
        for (var ticket : tickets) {
            var seat = event.seat(ticket.getEventSeatId());
            seats.add(new TicketEmailSender.Seat(seat.section(), seat.row(), seat.number(), seat.zone(),
                    seat.price(), seat.currency()));
            attachments.add(new Attachment("ticket-row%d-seat%d.pdf".formatted(seat.row(), seat.number()),
                    pdfOf(ticket, event, holder)));
        }
        return new TicketEmail(holder.getEmail(), holder.getFullName(), event.title(),
                dateFormat.format(event.startsAt()), venueLine(event), order.orderId(), seats, attachments);
    }

    private List<Ticket> ensureTickets(PaidOrder order) {
        Map<UUID, Ticket> existing = ticketRepository.findByOrderId(order.orderId()).stream()
                .collect(Collectors.toMap(Ticket::getOrderItemId, Function.identity()));
        List<Ticket> tickets = new ArrayList<>();
        for (PaidItem item : order.items()) {
            var ticket = existing.get(item.orderItemId());
            if (ticket == null) {
                ticket = ticketRepository.save(new Ticket(item.orderItemId(), order.orderId(), order.userId(),
                        order.eventId(), item.eventSeatId()));
            }
            tickets.add(ticket);
        }
        return tickets;
    }

    private byte[] pdfOf(Ticket ticket, TicketEventInfo event, User holder) {
        return storage.read(ticket.getPdfPath()).orElseGet(() -> {
            var seat = event.seat(ticket.getEventSeatId());
            var pdf = pdfGenerator.generate(new TicketContent(ticket.getCode(), holder.getFullName(), event.title(),
                    dateFormat.format(event.startsAt()), venueLine(event), seat.section(), seat.row(), seat.number(),
                    seat.zone(), seat.price(), seat.currency()));
            ticket.attachPdf(storage.save(ticket.getId(), pdf));
            return pdf;
        });
    }

    @Transactional(readOnly = true)
    public Page<TicketView> myTickets(UUID userId, Pageable pageable) {
        return ticketRepository.findByUserId(userId, pageable).map(TicketService::toView);
    }

    /** Файл зник (новий контейнер без тому, видалили вручну) — перегенеровуємо з даних у БД. */
    @Transactional
    public byte[] pdf(UUID ticketId, CurrentUser viewer) {
        var ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getUserId().equals(viewer.id()) || viewer.hasRole(Role.ADMIN.name()))
                .orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        var event = eventService.ticketInfo(ticket.getEventId(), List.of(ticket.getEventSeatId()));
        return pdfOf(ticket, event, userService.getById(ticket.getUserId()));
    }

    /** Перевірка на вході: VALID → USED рівно один раз, навіть якщо QR зчитали два сканери одночасно. */
    @Transactional
    public ScanResult scan(UUID eventId, String code) {
        var ticket = ticketRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!ticket.getEventId().equals(eventId)) {
            throw new BusinessRuleException("Ticket is for another event");
        }
        ticket.use(clock.instant());
        var seat = eventService.ticketInfo(eventId, List.of(ticket.getEventSeatId())).seat(ticket.getEventSeatId());
        return new ScanResult(ticket.getId(), ticket.getStatus(), ticket.getUsedAt(),
                seat.section(), seat.row(), seat.number());
    }

    private static String venueLine(TicketEventInfo event) {
        return "%s, %s, %s".formatted(event.venueName(), event.venueAddress(), event.city());
    }

    private static TicketView toView(Ticket ticket) {
        return new TicketView(ticket.getId(), ticket.getOrderId(), ticket.getEventId(), ticket.getEventSeatId(),
                ticket.getCode(), ticket.getStatus(), ticket.getUsedAt(), ticket.getCreatedAt());
    }
}
