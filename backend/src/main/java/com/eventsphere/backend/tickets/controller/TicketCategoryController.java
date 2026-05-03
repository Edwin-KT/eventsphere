package com.eventsphere.backend.tickets.controller;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.tickets.dto.CreateTicketCategoryRequest;
import com.eventsphere.backend.tickets.dto.TicketCategoryResponse;
import com.eventsphere.backend.tickets.dto.UpdateTicketCategoryRequest;
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

    /**
     * Public — anyone browsing events needs to see available categories and prices.
     */
    @GetMapping
    public ResponseEntity<List<TicketCategoryResponse>> getCategories(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ticketCategoryService.getCategories(eventId));
    }

    /**
     * ORGANIZER only — add a new ticket category (VIP, General, Early Bird, etc.)
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TicketCategoryResponse> addCategory(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateTicketCategoryRequest request,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ticketCategoryService.addCategory(eventId, request, organizer));
    }

    /**
     * ORGANIZER only — patch-style update (all fields optional).
     * Inventory changes are guarded in the service layer.
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TicketCategoryResponse> updateCategory(
            @PathVariable UUID eventId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateTicketCategoryRequest request,
            @AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(
                ticketCategoryService.updateCategory(eventId, categoryId, request, organizer)
        );
    }

    /**
     * ORGANIZER only — delete a category.
     * Blocked if any non-cancelled tickets exist for this category.
     */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID eventId,
            @PathVariable UUID categoryId,
            @AuthenticationPrincipal User organizer) {
        ticketCategoryService.deleteCategory(eventId, categoryId, organizer);
        return ResponseEntity.noContent().build();
    }
}