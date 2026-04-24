package com.eventsphere.backend.reservations.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles one inventory-decrement + reservation-persist attempt inside its own
 * {@code REQUIRES_NEW} transaction.
 *
 * <p>Keeping this in a separate Spring bean (not an inner method of
 * {@link ReservationService}) is intentional: Spring's AOP proxy only intercepts
 * calls that go through the proxy, so self-invocation inside the same bean would
 * skip the {@code @Transactional} advice entirely.
 *
 * <p>Package-private visibility keeps it out of the public API; it is only meant
 * to be used by {@link ReservationService}.
 */
@Component
@RequiredArgsConstructor
public class ReservationAttemptHelper {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final ReservationRepository reservationRepository;

    /** Reservation TTL: 10 minutes, matching the spec. */
    static final int RESERVATION_TTL_MINUTES = 10;

    /**
     * Attempts to decrement inventory and create a PENDING reservation.
     *
     * @throws ConflictException                       if available inventory is insufficient
     * @throws ObjectOptimisticLockingFailureException if a concurrent update stole the version
     *                                                 (caller should retry)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation attempt(UUID categoryId, User user, Event event, int quantity) {
        TicketCategory category = ticketCategoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("TicketCategory", categoryId));

        if (category.getAvailableInventory() < quantity) {
            throw new ConflictException("Insufficient inventory for category: " + category.getName());
        }

        category.setAvailableInventory(category.getAvailableInventory() - quantity);
        // saveAndFlush forces an immediate flush so that ObjectOptimisticLockingFailureException
        // is thrown now (within this REQUIRES_NEW transaction) rather than at outer commit time.
        ticketCategoryRepository.saveAndFlush(category);

        Reservation reservation = Reservation.builder()
                .user(user)
                .event(event)
                .ticketCategory(category)
                .quantity(quantity)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES))
                .build();

        return reservationRepository.save(reservation);
    }
}
