package com.eventsphere.backend.tickets.service;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ConflictException;
import com.eventsphere.backend.common.exception.ForbiddenException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.entity.EventStatus;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.tickets.dto.CreateTicketCategoryRequest;
import com.eventsphere.backend.tickets.dto.TicketCategoryResponse;
import com.eventsphere.backend.tickets.dto.UpdateTicketCategoryRequest;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.entity.TicketStatus;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import com.eventsphere.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    @Transactional
    public TicketCategoryResponse addCategory(UUID eventId,
                                              CreateTicketCategoryRequest request,
                                              User organizer) {
        Event event = fetchPublishedEvent(eventId);
        verifyOwnership(event, organizer);

        TicketCategory category = TicketCategory.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .totalInventory(request.getTotalInventory())
                // availableInventory starts equal to totalInventory — nothing sold yet
                .availableInventory(request.getTotalInventory())
                .build();

        return TicketCategoryResponse.fromEntity(ticketCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryResponse> getCategories(UUID eventId) {
        // Verify the event exists before listing its categories
        if (!eventRepository.existsById(eventId)) {
            throw ResourceNotFoundException.of("Event", eventId);
        }

        return ticketCategoryRepository.findByEventId(eventId)
                .stream()
                .map(TicketCategoryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TicketCategoryResponse updateCategory(UUID eventId,
                                                 UUID categoryId,
                                                 UpdateTicketCategoryRequest request,
                                                 User organizer) {
        Event event = fetchPublishedEvent(eventId);
        verifyOwnership(event, organizer);

        TicketCategory category = ticketCategoryRepository.findByIdAndEventId(categoryId, eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("TicketCategory", categoryId));

        if (request.getName() != null) {
            category.setName(request.getName());
        }

        if (request.getPrice() != null) {
            category.setPrice(request.getPrice());
        }

        if (request.getTotalInventory() != null) {
            int soldCount = category.getTotalInventory() - category.getAvailableInventory();
            int newTotal = request.getTotalInventory();

            if (newTotal < soldCount) {
                // Cannot reduce inventory below what's already been sold
                throw new ConflictException(
                        "Cannot set total inventory to " + newTotal +
                                ": " + soldCount + " ticket(s) have already been sold for this category"
                );
            }

            // Adjust availableInventory by the same delta as totalInventory
            int delta = newTotal - category.getTotalInventory();
            category.setTotalInventory(newTotal);
            category.setAvailableInventory(category.getAvailableInventory() + delta);
        }

        return TicketCategoryResponse.fromEntity(ticketCategoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID eventId, UUID categoryId, User organizer) {
        Event event = fetchPublishedEvent(eventId);
        verifyOwnership(event, organizer);

        TicketCategory category = ticketCategoryRepository.findByIdAndEventId(categoryId, eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("TicketCategory", categoryId));

        // Block deletion if any non-cancelled tickets exist for this category
        boolean hasActiveSales = ticketRepository.existsByTicketCategoryIdAndStatusNot(
                categoryId, TicketStatus.CANCELLED
        );

        if (hasActiveSales) {
            throw new ConflictException(
                    "Cannot delete category: tickets have already been sold. Cancel the event instead."
            );
        }

        ticketCategoryRepository.delete(category);
    }

    // --- Helpers ---

    private Event fetchPublishedEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Event", eventId));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Cannot modify categories of a cancelled event");
        }

        return event;
    }

    private void verifyOwnership(Event event, User organizer) {
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ForbiddenException("You are not the organizer of this event");
        }
    }
}