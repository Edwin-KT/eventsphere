package com.eventsphere.backend.tickets.repository;

import com.eventsphere.backend.tickets.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    List<TicketCategory> findByEventId(UUID eventId);

    /**
     * Atomically releases inventory without going through optimistic locking.
     * Used by the expiry scheduler, which must not fail due to version conflicts.
     * The LEAST guard prevents available_inventory from exceeding total_inventory.
     */
    @Modifying
    @Query(value = """
            UPDATE ticket_categories
               SET available_inventory = LEAST(total_inventory, available_inventory + :quantity),
                   updated_at          = now()
             WHERE id = :categoryId
            """, nativeQuery = true)
    void releaseInventory(@Param("categoryId") UUID categoryId, @Param("quantity") int quantity);
}
