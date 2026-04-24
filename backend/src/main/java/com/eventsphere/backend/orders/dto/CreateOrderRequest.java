package com.eventsphere.backend.orders.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotNull
    private UUID reservationId;
}
