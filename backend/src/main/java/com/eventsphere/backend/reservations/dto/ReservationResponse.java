package com.eventsphere.backend.reservations.dto;

import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ReservationResponse {

    private UUID reservationId;
    private ReservationStatus status;
    private LocalDateTime expiresAt;

    public static ReservationResponse fromEntity(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .status(reservation.getStatus())
                .expiresAt(reservation.getExpiresAt())
                .build();
    }
}
