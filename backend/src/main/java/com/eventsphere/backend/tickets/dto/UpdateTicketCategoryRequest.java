package com.eventsphere.backend.tickets.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTicketCategoryRequest {

    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String name;

    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    private BigDecimal price;

    // Service enforces: newTotal >= already-sold count (totalInventory - availableInventory)
    @Min(value = 1, message = "Total inventory must be at least 1")
    private Integer totalInventory;
}