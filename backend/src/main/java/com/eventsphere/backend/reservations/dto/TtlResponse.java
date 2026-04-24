package com.eventsphere.backend.reservations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TtlResponse {

    private UUID reservationId;
    /** Remaining TTL in seconds; 0 when the reservation has expired. */
    private long ttlSeconds;

    public static TtlResponse of(UUID reservationId, long ttlSeconds) {
        return TtlResponse.builder()
                .reservationId(reservationId)
                .ttlSeconds(ttlSeconds)
                .build();
    }
}
