package com.eventsphere.backend.tickets.dto;

import com.eventsphere.backend.tickets.entity.TicketCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CategoryResponse {

    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal price;
    private int totalInventory;
    private int availableInventory;
    private LocalDateTime createdAt;

    public static CategoryResponse fromEntity(TicketCategory category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .eventId(category.getEvent().getId())
                .name(category.getName())
                .price(category.getPrice())
                .totalInventory(category.getTotalInventory())
                .availableInventory(category.getAvailableInventory())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
