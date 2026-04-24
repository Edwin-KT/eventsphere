package com.eventsphere.backend.tickets.controller;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.tickets.dto.CategoryResponse;
import com.eventsphere.backend.tickets.dto.CreateCategoryRequest;
import com.eventsphere.backend.tickets.service.TicketCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/categories")
@RequiredArgsConstructor
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;

    /** Public – anyone can browse available categories for an event. */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ticketCategoryService.listCategories(eventId));
    }

    /** Organizer-only – create a new ticket category for an event they own. */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ticketCategoryService.createCategory(eventId, request, organizer));
    }
}
