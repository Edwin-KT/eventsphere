package com.eventsphere.backend.orders.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.common.exception.UnprocessableEntityException;
import com.eventsphere.backend.orders.dto.CreateOrderRequest;
import com.eventsphere.backend.orders.dto.OrderResponse;
import com.eventsphere.backend.orders.entity.Order;
import com.eventsphere.backend.orders.repository.OrderRepository;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.reservations.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles order creation with idempotency-key semantics.
 *
 * <p>Idempotency is scoped per <em>(user_id, idempotency_key)</em> pair — a key is not
 * globally unique but is unique within a user's request space.
 *
 * <p>Rules:
 * <ul>
 *   <li>First request → creates the order, returns 201.</li>
 *   <li>Same user + same key + same {@code reservationId} → returns the existing order, 200.</li>
 *   <li>Same user + same key + different {@code reservationId} → 409 Conflict.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    /**
     * Result carrier so the controller can distinguish 201 from 200.
     */
    public record OrderResult(OrderResponse order, boolean isNew) {}

    @Transactional
    public OrderResult createOrder(CreateOrderRequest request, String idempotencyKey, User user) {
        // ── Idempotency check ──────────────────────────────────────────────────────
        Optional<Order> existing = orderRepository
                .findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);

        if (existing.isPresent()) {
            Order existingOrder = existing.get();
            // Same payload → return the already-created order (idempotent replay)
            if (existingOrder.getReservation() != null
                    && existingOrder.getReservation().getId().equals(request.getReservationId())) {
                return new OrderResult(OrderResponse.fromEntity(existingOrder), false);
            }
            // Different payload → conflict
            throw new ConflictException(
                    "Idempotency key '" + idempotencyKey + "' was already used with a different reservation");
        }

        // ── Validate reservation ────────────────────────────────────────────────
        Reservation reservation = reservationRepository
                .findByIdAndUserId(request.getReservationId(), user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", request.getReservationId()));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new UnprocessableEntityException(
                    "Reservation is not in PENDING state (current: " + reservation.getStatus() + ")");
        }
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnprocessableEntityException("Reservation has expired");
        }

        // ── Create order and complete the reservation ───────────────────────────
        Order order = Order.builder()
                .user(user)
                .reservation(reservation)
                .idempotencyKey(idempotencyKey)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Mark the reservation COMPLETED so it cannot be reused
        reservationService.completeReservation(reservation.getId(), user);

        return new OrderResult(OrderResponse.fromEntity(savedOrder), true);
    }
}
