package com.eventsphere.backend.tickets.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTicketCategoryRequest {

    @NotBlank
    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String name;

    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    private BigDecimal price;

    @NotNull
    @Min(value = 1, message = "Total inventory must be at least 1")
    private Integer totalInventory;
}