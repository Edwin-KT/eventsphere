package com.eventsphere.backend.tickets.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCategoryRequest {

    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0.00")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Min(1)
    private Integer totalInventory;
}
