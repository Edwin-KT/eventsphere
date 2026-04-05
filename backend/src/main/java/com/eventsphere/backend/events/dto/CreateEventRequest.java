package com.eventsphere.backend.events.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateEventRequest {
    @NotBlank
    @Size(min = 1, max = 255)
    private String title;

    private String description;

    @NotBlank
    private String location;

    @NotNull
    @Future
    private LocalDateTime eventDate;

    private String imageUrl;
}
