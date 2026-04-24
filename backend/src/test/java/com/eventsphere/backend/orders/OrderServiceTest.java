package com.eventsphere.backend.orders;

import com.eventsphere.backend.auth.entity.Role;
import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.UnprocessableEntityException;
import com.eventsphere.backend.orders.dto.CreateOrderRequest;
import com.eventsphere.backend.orders.entity.Order;
import com.eventsphere.backend.orders.entity.OrderStatus;
import com.eventsphere.backend.orders.repository.OrderRepository;
import com.eventsphere.backend.orders.service.OrderService;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.reservations.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationService reservationService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Reservation reservation;
    private UUID reservationId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        reservationId   = UUID.randomUUID();
        idempotencyKey  = UUID.randomUUID().toString();
        user = User.builder().id(UUID.randomUUID()).email("user@test.com").role(Role.USER).build();
        reservation = Reservation.builder()
                .id(reservationId)
                .user(user)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private CreateOrderRequest requestFor(UUID resId) {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setReservationId(resId);
        return req;
    }

    // ── First-time order creation ──────────────────────────────────────────────

    @Test
    void createOrder_returns201_onFirstRequest() {
        Order savedOrder = Order.builder()
                .id(UUID.randomUUID()).user(user).reservation(reservation)
                .idempotencyKey(idempotencyKey).status(OrderStatus.PENDING).build();

        when(orderRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey))
                .thenReturn(Optional.empty());
        when(reservationRepository.findByIdAndUserId(reservationId, user.getId()))
                .thenReturn(Optional.of(reservation));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderService.OrderResult result = orderService.createOrder(requestFor(reservationId), idempotencyKey, user);

        assertThat(result.isNew()).isTrue();
        assertThat(result.order().getId()).isEqualTo(savedOrder.getId());
        verify(reservationService).completeReservation(reservationId, user);
    }

    // ── Idempotent replay (same payload) ──────────────────────────────────────

    @Test
    void createOrder_returns200_forExactReplay() {
        Order existingOrder = Order.builder()
                .id(UUID.randomUUID()).user(user).reservation(reservation)
                .idempotencyKey(idempotencyKey).status(OrderStatus.PENDING).build();

        when(orderRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey))
                .thenReturn(Optional.of(existingOrder));

        OrderService.OrderResult result = orderService.createOrder(requestFor(reservationId), idempotencyKey, user);

        assertThat(result.isNew()).isFalse();
        assertThat(result.order().getId()).isEqualTo(existingOrder.getId());
        // Must NOT create another order or complete the reservation again
        verify(orderRepository, never()).save(any());
        verify(reservationService, never()).completeReservation(any(), any());
    }

    // ── Idempotency key conflict (different payload) ───────────────────────────

    @Test
    void createOrder_throws409_whenKeyReusedWithDifferentReservation() {
        UUID differentReservationId = UUID.randomUUID();
        Reservation otherReservation = Reservation.builder().id(differentReservationId).build();
        Order existingOrder = Order.builder()
                .id(UUID.randomUUID()).user(user).reservation(otherReservation)
                .idempotencyKey(idempotencyKey).build();

        when(orderRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey))
                .thenReturn(Optional.of(existingOrder));

        // Request references a DIFFERENT reservation → conflict
        assertThatThrownBy(() -> orderService.createOrder(requestFor(reservationId), idempotencyKey, user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(idempotencyKey);
    }

    // ── Reservation validation ─────────────────────────────────────────────────

    @Test
    void createOrder_throws422_whenReservationIsExpiredStatus() {
        reservation.setStatus(ReservationStatus.EXPIRED);

        when(orderRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey))
                .thenReturn(Optional.empty());
        when(reservationRepository.findByIdAndUserId(reservationId, user.getId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> orderService.createOrder(requestFor(reservationId), idempotencyKey, user))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void createOrder_throws422_whenReservationTtlHasPassed() {
        reservation.setExpiresAt(LocalDateTime.now().minusSeconds(1)); // expired TTL

        when(orderRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey))
                .thenReturn(Optional.empty());
        when(reservationRepository.findByIdAndUserId(reservationId, user.getId()))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> orderService.createOrder(requestFor(reservationId), idempotencyKey, user))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("expired");
    }
}
