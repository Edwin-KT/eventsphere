package com.eventsphere.backend.reservations.controller;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.reservations.dto.CreateReservationRequest;
import com.eventsphere.backend.reservations.dto.ReservationResponse;
import com.eventsphere.backend.reservations.dto.TtlResponse;
import com.eventsphere.backend.reservations.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * Creates a new PENDING reservation.
     *
     * <ul>
     *   <li>201 Created on success</li>
     *   <li>409 Conflict if inventory is insufficient or lock/version conflict exhausted</li>
     *   <li>503 Service Unavailable if Redis cannot be reached</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reservationService.createReservation(request, user));
    }

    /**
     * Returns the remaining TTL for a reservation.
     *
     * <ul>
     *   <li>200 OK with {@code ttlSeconds}</li>
     *   <li>404 Not Found if reservation is missing or belongs to another user</li>
     * </ul>
     */
    @GetMapping("/{id}/ttl")
    public ResponseEntity<TtlResponse> getReservationTtl(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reservationService.getReservationTtl(id, user));
    }
}
