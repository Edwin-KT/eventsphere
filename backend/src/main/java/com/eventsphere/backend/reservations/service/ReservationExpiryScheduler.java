package com.eventsphere.backend.reservations.service;

import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Background job that periodically scans for PENDING reservations whose TTL has elapsed,
 * atomically expires them, and releases their inventory back to the pool.
 *
 * <p><b>Idempotency guarantee:</b> each reservation is processed through
 * {@link ReservationExpireHelper#expireOne(UUID)} which issues a conditional
 * {@code UPDATE … WHERE status = 'PENDING'} before touching inventory.  If two
 * instances pick up the same candidate concurrently, only the first {@code UPDATE}
 * will match (the second finds 0 rows) and inventory is released exactly once.
 *
 * <p>{@code fixedDelay} prevents overlap within the same JVM; a distributed lock
 * (e.g. Redisson {@code RLock}) would be needed for true multi-instance safety but
 * is out of scope for this portfolio phase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final ReservationExpireHelper expireHelper;

    @Scheduled(fixedDelay = 30_000) // every 30 seconds; fixedDelay avoids overlapping runs
    public void expireStaleReservations() {
        List<UUID> candidateIds = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now())
                .stream()
                .map(r -> r.getId())
                .toList();

        if (candidateIds.isEmpty()) {
            return;
        }

        log.info("Expiry scheduler found {} expired PENDING reservations", candidateIds.size());
        candidateIds.forEach(id -> {
            try {
                expireHelper.expireOne(id);
            } catch (Exception ex) {
                log.error("Failed to expire reservation {}: {}", id, ex.getMessage(), ex);
            }
        });
    }
}
