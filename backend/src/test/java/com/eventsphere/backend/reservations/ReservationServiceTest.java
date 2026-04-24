package com.eventsphere.backend.reservations;

import com.eventsphere.backend.auth.entity.Role;
import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.ServiceUnavailableException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.reservations.dto.CreateReservationRequest;
import com.eventsphere.backend.reservations.dto.ReservationResponse;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.reservations.service.RedisLockService;
import com.eventsphere.backend.reservations.service.ReservationAttemptHelper;
import com.eventsphere.backend.reservations.service.ReservationService;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private TicketCategoryRepository ticketCategoryRepository;
    @Mock private EventRepository eventRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private ReservationAttemptHelper attemptHelper;

    @InjectMocks
    private ReservationService reservationService;

    private User user;
    private Event event;
    private TicketCategory category;
    private UUID eventId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        eventId    = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        user = User.builder().id(UUID.randomUUID()).email("user@test.com").role(Role.USER).build();
        event = Event.builder().id(eventId).title("Concert").build();
        category = TicketCategory.builder()
                .id(categoryId).event(event).name("General")
                .price(BigDecimal.valueOf(25)).totalInventory(100).availableInventory(10)
                .build();
    }

    private CreateReservationRequest buildRequest(int qty) {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setEventId(eventId);
        req.setCategoryId(categoryId);
        req.setQuantity(qty);
        return req;
    }

    // ── Redis-lock acquisition ─────────────────────────────────────────────────

    @Test
    void createReservation_throws503_whenRedisIsDown() {
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisLockService.tryLock(anyString(), anyString(), anyLong()))
                .thenThrow(new ServiceUnavailableException("Redis unreachable"));

        assertThatThrownBy(() -> reservationService.createReservation(buildRequest(1), user))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Redis");
    }

    @Test
    void createReservation_throws409_whenLockAlreadyHeld() {
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisLockService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> reservationService.createReservation(buildRequest(1), user))
                .isInstanceOf(ConflictException.class);
    }

    // ── Optimistic lock retries ────────────────────────────────────────────────

    @Test
    void createReservation_succeedsOnSecondAttempt_afterOptimisticConflict() {
        Reservation savedReservation = Reservation.builder()
                .id(UUID.randomUUID())
                .user(user).event(event).ticketCategory(category)
                .quantity(2).status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisLockService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        // First attempt → optimistic lock failure; second attempt → success
        when(attemptHelper.attempt(eq(categoryId), eq(user), eq(event), eq(2)))
                .thenThrow(new ObjectOptimisticLockingFailureException(TicketCategory.class, categoryId))
                .thenReturn(savedReservation);

        ReservationResponse response = reservationService.createReservation(buildRequest(2), user);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(attemptHelper, times(2)).attempt(any(), any(), any(), anyInt());
        verify(redisLockService).unlock(anyString(), anyString());
    }

    @Test
    void createReservation_throws409_afterAllRetriesExhausted() {
        when(ticketCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisLockService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(attemptHelper.attempt(any(), any(), any(), anyInt()))
                .thenThrow(new ObjectOptimisticLockingFailureException(TicketCategory.class, categoryId));

        assertThatThrownBy(() -> reservationService.createReservation(buildRequest(1), user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 attempts");

        // Should have tried exactly MAX_RETRIES times
        verify(attemptHelper, times(ReservationService.MAX_RETRIES)).attempt(any(), any(), any(), anyInt());
        // Lock must always be released, even on failure
        verify(redisLockService).unlock(anyString(), anyString());
    }

    // ── TTL endpoint ───────────────────────────────────────────────────────────

    @Test
    void getReservationTtl_returnsPositiveTtl_forActivePendingReservation() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId).user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(reservationRepository.findByIdAndUserId(reservationId, user.getId()))
                .thenReturn(Optional.of(reservation));

        var ttlResponse = reservationService.getReservationTtl(reservationId, user);

        assertThat(ttlResponse.getTtlSeconds()).isGreaterThan(0);
        assertThat(ttlResponse.getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void getReservationTtl_returnsZero_forAlreadyExpiredReservation() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId).user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(reservationRepository.findByIdAndUserId(reservationId, user.getId()))
                .thenReturn(Optional.of(reservation));

        var ttlResponse = reservationService.getReservationTtl(reservationId, user);

        assertThat(ttlResponse.getTtlSeconds()).isEqualTo(0);
    }
}
