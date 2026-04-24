package com.eventsphere.backend.tickets.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ForbiddenException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.tickets.dto.CategoryResponse;
import com.eventsphere.backend.tickets.dto.CreateCategoryRequest;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID eventId) {
        // Verify event exists before returning its categories
        if (!eventRepository.existsById(eventId)) {
            throw ResourceNotFoundException.of("Event", eventId);
        }
        return ticketCategoryRepository.findByEventId(eventId)
                .stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID eventId, CreateCategoryRequest request, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Event", eventId));

        // Only the event's organizer may add categories
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ForbiddenException("Only the event organizer can create ticket categories");
        }

        TicketCategory category = TicketCategory.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .totalInventory(request.getTotalInventory())
                .availableInventory(request.getTotalInventory()) // starts fully available
                .build();

        return CategoryResponse.fromEntity(ticketCategoryRepository.save(category));
    }
}
