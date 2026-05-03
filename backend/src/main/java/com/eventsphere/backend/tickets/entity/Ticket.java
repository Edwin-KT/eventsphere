package com.eventsphere.backend.tickets.entity;

import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.orders.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // An order can contain multiple tickets (different categories)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_category_id", nullable = false)
    private TicketCategory ticketCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TicketStatus status = TicketStatus.VALID;

    // HMAC-signed hash stored here; the raw value is encoded into the QR code
    @Column(name = "qr_code_hash", nullable = false, unique = true, length = 255)
    private String qrCodeHash;

    // Set to true by the async PDF-generation consumer after RabbitMQ processes the job
    @Column(name = "pdf_generated", nullable = false)
    @Builder.Default
    private boolean pdfGenerated = false;

    // Audit trail — legal requirement for ticketing systems
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}