package com.eventsphere.backend.tickets.repository;

import com.eventsphere.backend.tickets.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {

    List<TicketCategory> findByEventId(UUID eventId);

    // Used to verify a category belongs to the event in the URL path
    Optional<TicketCategory> findByIdAndEventId(UUID id, UUID eventId);

    // Used to check whether an event has any categories before allowing event deletion (future)
    boolean existsByEventId(UUID eventId);
}