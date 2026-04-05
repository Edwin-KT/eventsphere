package com.eventsphere.backend.events.dto;

import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class EventResponse {
    private UUID id;
    private EventStatus status;
    private String organizerName;
    private String title;
    private String description;
    private String location;
    private LocalDateTime eventDate;
    private String imageUrl;

    public static EventResponse fromEntity(Event event) {
        if (event == null) {
            return null;
        }

        return EventResponse.builder()
                .id(event.getId())
                .status(event.getStatus())
                .organizerName(event.getOrganizer().getFullName())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .eventDate(event.getEventDate())
                .imageUrl(event.getImageUrl())
                .build();
    }
}
