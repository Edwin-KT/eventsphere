package com.eventsphere.backend.reservations.repository;

import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Finds all PENDING reservations whose TTL has passed.
     * Used by the expiry scheduler.
     */
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime cutoff);

    /**
     * Conditionally sets a reservation's status to EXPIRED only when it is still PENDING.
     * Returns 1 if the row was updated (this instance owns the expiry), 0 if already processed.
     * This prevents double-release of inventory when multiple scheduler instances run simultaneously.
     */
    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'EXPIRED' WHERE r.id = :id AND r.status = 'PENDING'")
    int expireIfPending(@Param("id") UUID id);

    Optional<Reservation> findByIdAndUserId(UUID id, UUID userId);
}
