package com.eventsphere.backend.events.repository;

import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByStatusAndEventDateAfter(EventStatus status, LocalDateTime eventDate, Pageable pageable);
}