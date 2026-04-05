package com.eventsphere.backend.events.controller;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.events.dto.CreateEventRequest;
import com.eventsphere.backend.events.dto.EventResponse;
import com.eventsphere.backend.events.dto.UpdateEventRequest;
import com.eventsphere.backend.events.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<Page<EventResponse>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LocalDateTime from) {
        // call service, return 200
        return ResponseEntity.ok(eventService.getEvents(page, size, from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request,organizer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(eventService.updateEvent(id,request,organizer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> cancelEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal User organizer) {
        eventService.cancelEvent(id,organizer);
        return ResponseEntity.noContent().build();
    }
}