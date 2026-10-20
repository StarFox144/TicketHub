package com.tickethub.ticket.api;

import com.tickethub.common.security.CurrentUser;
import com.tickethub.ticket.api.dto.ScanRequest;
import com.tickethub.ticket.api.dto.ScanResult;
import com.tickethub.ticket.api.dto.TicketView;
import com.tickethub.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Квитки: завантаження і сканування на вході")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Мої квитки")
    public Page<TicketView> myTickets(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        return ticketService.myTickets(user.id(), pageable);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Завантажити квиток (PDF з QR-кодом)")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,
                                      @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        var disposition = ContentDisposition.attachment().filename("ticket-" + id + ".pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(ticketService.pdf(id, user));
    }

    @PostMapping("/scan")
    @PreAuthorize("hasRole('ORGANIZER') and @eventSecurity.isOwner(#request.eventId(), authentication)")
    @Operation(summary = "Перевірити код з QR і позначити квиток використаним (організатор події)",
            description = "409 — квиток уже пройшов (час у usedAt); 422 — квиток на іншу подію; 404 — такого коду немає")
    public ScanResult scan(@Valid @RequestBody ScanRequest request) {
        return ticketService.scan(request.eventId(), request.code());
    }
}
