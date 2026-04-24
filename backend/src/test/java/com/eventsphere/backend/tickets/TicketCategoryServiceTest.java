package com.eventsphere.backend.tickets;

import com.eventsphere.backend.auth.entity.Role;
import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.common.exception.ForbiddenException;
import com.eventsphere.backend.common.exception.ResourceNotFoundException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.tickets.dto.CategoryResponse;
import com.eventsphere.backend.tickets.dto.CreateCategoryRequest;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import com.eventsphere.backend.tickets.service.TicketCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCategoryServiceTest {

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private TicketCategoryService ticketCategoryService;

    private User organizer;
    private User otherUser;
    private Event event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        organizer = User.builder().id(UUID.randomUUID()).email("org@test.com").role(Role.ORGANIZER).build();
        otherUser = User.builder().id(UUID.randomUUID()).email("other@test.com").role(Role.ORGANIZER).build();
        event = Event.builder().id(eventId).organizer(organizer).title("Test Event").build();
    }

    // ── listCategories ─────────────────────────────────────────────────────────

    @Test
    void listCategories_returnsAllCategoriesForEvent() {
        TicketCategory cat = TicketCategory.builder()
                .id(UUID.randomUUID()).event(event).name("VIP")
                .price(BigDecimal.valueOf(100)).totalInventory(50).availableInventory(50)
                .build();
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(ticketCategoryRepository.findByEventId(eventId)).thenReturn(List.of(cat));

        List<CategoryResponse> result = ticketCategoryService.listCategories(eventId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("VIP");
    }

    @Test
    void listCategories_throwsNotFound_whenEventDoesNotExist() {
        when(eventRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> ticketCategoryService.listCategories(eventId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createCategory ─────────────────────────────────────────────────────────

    @Test
    void createCategory_succeeds_forEventOwner() {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("General");
        req.setPrice(BigDecimal.valueOf(50));
        req.setTotalInventory(200);

        TicketCategory saved = TicketCategory.builder()
                .id(UUID.randomUUID()).event(event).name("General")
                .price(BigDecimal.valueOf(50)).totalInventory(200).availableInventory(200)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketCategoryRepository.save(any())).thenReturn(saved);

        CategoryResponse response = ticketCategoryService.createCategory(eventId, req, organizer);

        assertThat(response.getName()).isEqualTo("General");
        assertThat(response.getTotalInventory()).isEqualTo(200);
        assertThat(response.getAvailableInventory()).isEqualTo(200);
        verify(ticketCategoryRepository).save(any(TicketCategory.class));
    }

    @Test
    void createCategory_throwsForbidden_forNonOwner() {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("General");
        req.setPrice(BigDecimal.valueOf(50));
        req.setTotalInventory(100);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // otherUser is not the organizer of 'event'
        assertThatThrownBy(() -> ticketCategoryService.createCategory(eventId, req, otherUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("organizer");
    }

    @Test
    void createCategory_throwsNotFound_whenEventMissing() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("VIP");
        req.setPrice(BigDecimal.TEN);
        req.setTotalInventory(10);

        assertThatThrownBy(() -> ticketCategoryService.createCategory(eventId, req, organizer))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
