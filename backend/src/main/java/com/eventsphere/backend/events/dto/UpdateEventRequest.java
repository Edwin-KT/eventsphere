package com.eventsphere.backend.events.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class UpdateEventRequest {
    private String title;
    private String description;
    private String location;
    private LocalDateTime eventDate;
    private String imageUrl;
}
