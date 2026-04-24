package com.eventsphere.backend.reservations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReservationRequest {

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID categoryId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
