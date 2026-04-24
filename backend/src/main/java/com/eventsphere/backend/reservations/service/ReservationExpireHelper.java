package com.eventsphere.backend.reservations.service;

import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Processes a single reservation expiry inside its own {@code REQUIRES_NEW} transaction.
 *
 * <p>Separating this from {@link ReservationExpiryScheduler} is required so that
 * Spring's transactional proxy is applied correctly (self-invocation bypasses AOP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpireHelper {

    private final ReservationRepository reservationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    /**
     * Atomically expires a reservation and releases its inventory.
     *
     * <p>The conditional {@code UPDATE … WHERE status = 'PENDING'} ensures idempotency:
     * if another instance already expired this reservation, {@code rowsUpdated == 0} and
     * inventory is not touched again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(UUID reservationId) {
        int rowsUpdated = reservationRepository.expireIfPending(
                reservationId, ReservationStatus.EXPIRED, ReservationStatus.PENDING);

        if (rowsUpdated == 0) {
            // Already expired or completed by another instance — nothing to do.
            log.debug("Reservation {} was already processed; skipping inventory release", reservationId);
            return;
        }

        // Release the inventory only when *this* transaction was the one that expired it
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            UUID categoryId = reservation.getTicketCategory().getId();
            int quantity = reservation.getQuantity();
            ticketCategoryRepository.releaseInventory(categoryId, quantity);
            log.debug("Released {} unit(s) of category {} after expiry of reservation {}",
                    quantity, categoryId, reservationId);
        });
    }
}
