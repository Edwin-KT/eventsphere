package com.eventsphere.backend.tickets.repository;

import com.eventsphere.backend.tickets.entity.Ticket;
import com.eventsphere.backend.tickets.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByEventId(UUID eventId);

    // Used by "My Tickets" page — fetch all tickets for a user across orders
    @Query("SELECT t FROM Ticket t JOIN t.order o WHERE o.user.id = :userId")
    List<Ticket> findByUserId(@Param("userId") UUID userId);

    Optional<Ticket> findByQrCodeHash(String qrCodeHash);

    // Atomic check-in: UPDATE tickets SET status = 'USED' WHERE id = ? AND status = 'VALID'
    // Returns the number of rows affected — must be 1 for a successful check-in.
    // This is the race-condition-safe approach: no SELECT + UPDATE, one atomic operation.
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'USED' WHERE t.id = :id AND t.status = com.eventsphere.backend.tickets.entity.TicketStatus.VALID")
    int markAsUsedIfValid(@Param("id") UUID id);

    // Used by the PDF async consumer to flip the flag after generating the PDF
    @Modifying
    @Query("UPDATE Ticket t SET t.pdfGenerated = true WHERE t.id = :id")
    void markPdfGenerated(@Param("id") UUID id);

    boolean existsByTicketCategoryIdAndStatusNot(UUID ticketCategoryId, TicketStatus status);
}