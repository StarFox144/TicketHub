package com.tickethub.booking.api;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.api.dto.OrderView;
import com.tickethub.booking.service.OrderService;
import com.tickethub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Бронювання місць і замовлення")
public class BookingController {

    private final OrderService orderService;

    public BookingController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Зарезервувати місця на 10 хвилин (все або нічого)",
            description = "201 — нове замовлення; 200 — повтор із тим самим Idempotency-Key; "
                    + "409 — частина місць уже зайнята (список у unavailableSeatIds)")
    public ResponseEntity<OrderView> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        var placed = orderService.placeOrder(user.id(), request, idempotencyKey);
        if (!placed.created()) {
            return ResponseEntity.ok(placed.order());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(placed.order().orderId()).toUri();
        return ResponseEntity.created(location).body(placed.order());
    }

    @GetMapping
    @Operation(summary = "Мої замовлення")
    public Page<OrderView> myOrders(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        return orderService.listForUser(user.id(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Деталі замовлення і час до кінця резерву")
    public OrderView get(@PathVariable UUID id,
                         @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        return orderService.get(id, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Скасувати PENDING-замовлення і звільнити місця")
    public OrderView cancel(@PathVariable UUID id,
                            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        return orderService.cancel(id, user.id());
    }
}
