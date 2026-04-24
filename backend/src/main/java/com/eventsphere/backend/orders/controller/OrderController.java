package com.eventsphere.backend.orders.controller;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.orders.dto.CreateOrderRequest;
import com.eventsphere.backend.orders.dto.OrderResponse;
import com.eventsphere.backend.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order for an existing PENDING reservation.
     *
     * <p>The {@code X-Idempotency-Key} header is required. Its semantics, per user:
     * <ul>
     *   <li>First request → 201 Created</li>
     *   <li>Exact replay (same reservationId) → 200 OK with the original order</li>
     *   <li>Same key, different reservationId → 409 Conflict</li>
     * </ul>
     *
     * @param idempotencyKey unique key supplied by the client (UUID recommended)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user) {

        OrderService.OrderResult result = orderService.createOrder(request, idempotencyKey, user);

        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.order());
    }
}
