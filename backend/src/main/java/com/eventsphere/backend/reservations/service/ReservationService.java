package com.eventsphere.backend.reservations.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.common.exception.ServiceUnavailableException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.reservations.dto.CreateReservationRequest;
import com.eventsphere.backend.reservations.dto.ReservationResponse;
import com.eventsphere.backend.reservations.dto.TtlResponse;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Orchestrates the ticket reservation flow:
 *
 * <ol>
 *   <li>Acquires a short-lived Redis lock on the category (prevents thundering herd from
 *       simultaneously reserving the last seat; lock TTL = 30 s).</li>
 *   <li>Attempts to decrement {@code available_inventory} with optimistic locking via a
 *       {@link ReservationAttemptHelper} running in its own {@code REQUIRES_NEW} transaction.
 *       Retries up to {@value #MAX_RETRIES} times on {@code ObjectOptimisticLockingFailureException}
 *       with a short back-off.</li>
 *   <li>On exhausted retries returns HTTP 409; on Redis failure returns HTTP 503.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    public static final int MAX_RETRIES = 3;
    private static final long LOCK_TTL_SECONDS = 30;
    private static final long RETRY_BACKOFF_MS = 50;

    private final ReservationRepository reservationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventRepository eventRepository;
    private final RedisLockService redisLockService;
    private final ReservationAttemptHelper attemptHelper;

    /**
     * Creates a PENDING reservation for the given category.
     *
     * @throws ServiceUnavailableException if Redis is unavailable
     * @throws ConflictException           if the lock cannot be acquired (another request holds it)
     *                                     or if all retry attempts fail
     */
    public ReservationResponse createReservation(CreateReservationRequest request, User user) {
        // Validate that the category belongs to the requested event
        TicketCategory category = ticketCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("TicketCategory", request.getCategoryId()));

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> ResourceNotFoundException.of("Event", request.getEventId()));

        if (!category.getEvent().getId().equals(event.getId())) {
            throw new ResourceNotFoundException(
                    "Category " + request.getCategoryId() + " does not belong to event " + request.getEventId());
        }

        String lockKey = "inventory:lock:" + request.getCategoryId();
        String lockValue = UUID.randomUUID().toString();

        // Throws ServiceUnavailableException if Redis is down
        boolean locked = redisLockService.tryLock(lockKey, lockValue, LOCK_TTL_SECONDS);
        if (!locked) {
            throw new ConflictException("Category is temporarily locked by another request; please retry shortly");
        }

        try {
            return retryInventoryUpdate(request.getCategoryId(), user, event, request.getQuantity());
        } finally {
            redisLockService.unlock(lockKey, lockValue);
        }
    }

    private ReservationResponse retryInventoryUpdate(UUID categoryId, User user, Event event, int quantity) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Reservation reservation = attemptHelper.attempt(categoryId, user, event, quantity);
                return ReservationResponse.fromEntity(reservation);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.debug("Optimistic lock conflict on category {} (attempt {}/{})",
                        categoryId, attempt + 1, MAX_RETRIES);
                if (attempt < MAX_RETRIES - 1) {
                    backOff();
                }
            }
        }
        throw new ConflictException(
                "Reservation failed after " + MAX_RETRIES + " attempts due to concurrent inventory updates");
    }

    private void backOff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the remaining TTL for a reservation.
     *
     * @throws ResourceNotFoundException if the reservation does not exist or belongs to another user
     */
    @Transactional(readOnly = true)
    public TtlResponse getReservationTtl(UUID reservationId, User user) {
        Reservation reservation = reservationRepository
                .findByIdAndUserId(reservationId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));

        long ttlSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), reservation.getExpiresAt());
        return TtlResponse.of(reservation.getId(), Math.max(0, ttlSeconds));
    }

    /**
     * Marks a reservation COMPLETED (called by the order service after successful order creation).
     */
    @Transactional
    public void completeReservation(UUID reservationId, User user) {
        Reservation reservation = reservationRepository
                .findByIdAndUserId(reservationId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ConflictException("Reservation is not in PENDING state: " + reservation.getStatus());
        }
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Reservation has already expired");
        }

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);
    }
}
