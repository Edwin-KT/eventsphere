package com.eventsphere.backend.events.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.events.dto.CreateEventRequest;
import com.eventsphere.backend.events.dto.EventResponse;
import com.eventsphere.backend.events.dto.UpdateEventRequest;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.entity.EventStatus;
import com.eventsphere.backend.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {

        Event event = Event.builder()
                .organizer(organizer)
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .eventDate(request.getEventDate())
                .imageUrl(request.getImageUrl())
                .build();
        Event savedEvent = eventRepository.save(event);

        return EventResponse.fromEntity(savedEvent);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getEvents(int page, int size, LocalDateTime from) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("eventDate").ascending());

        Page<Event> eventPage;

        if(from == null) {
            eventPage = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);
        } else{
            eventPage = eventRepository.findByStatusAndEventDateAfter(EventStatus.PUBLISHED, from, pageable);
        }

        return eventPage.map(EventResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return EventResponse.fromEntity(event);
    }

    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request, User organizer) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (!checkOwnership(event.getOrganizer(), organizer)) {
            throw new RuntimeException("You don't own this event");
        }

        if(request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if(request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if(request.getLocation() != null) {
            event.setLocation(request.getLocation());
        }
        if(request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if(request.getImageUrl() != null) {
            event.setImageUrl(request.getImageUrl());
        }

        Event savedEvent = eventRepository.save(event);

        return EventResponse.fromEntity(savedEvent);
    }

    @Transactional
    public void cancelEvent(UUID id, User organizer) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (!checkOwnership(event.getOrganizer(), organizer)) {
            throw new RuntimeException("You don't own this event");
        }

        event.setStatus(EventStatus.CANCELLED);
    }

    private boolean checkOwnership(User eventOrganizer, User askingOrganizer){
        return eventOrganizer.getId().equals(askingOrganizer.getId());
    }
}
