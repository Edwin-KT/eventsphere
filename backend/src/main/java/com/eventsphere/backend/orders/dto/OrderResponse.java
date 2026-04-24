package com.eventsphere.backend.orders.dto;

import com.eventsphere.backend.orders.entity.Order;
import com.eventsphere.backend.orders.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private UUID reservationId;
    private OrderStatus status;
    private String idempotencyKey;
    private LocalDateTime createdAt;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .reservationId(order.getReservation() != null ? order.getReservation().getId() : null)
                .status(order.getStatus())
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
