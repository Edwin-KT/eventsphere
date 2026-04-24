package com.eventsphere.backend;

import com.eventsphere.backend.TestcontainersConfiguration;
import com.eventsphere.backend.auth.entity.Role;
import com.eventsphere.backend.auth.entity.User;
import com.eventsphere.backend.auth.repository.UserRepository;
import com.eventsphere.backend.common.exception.ServiceUnavailableException;
import com.eventsphere.backend.events.entity.Event;
import com.eventsphere.backend.events.repository.EventRepository;
import com.eventsphere.backend.reservations.dto.CreateReservationRequest;
import com.eventsphere.backend.reservations.entity.Reservation;
import com.eventsphere.backend.reservations.entity.ReservationStatus;
import com.eventsphere.backend.reservations.repository.ReservationRepository;
import com.eventsphere.backend.reservations.service.RedisLockService;
import com.eventsphere.backend.reservations.service.ReservationExpireHelper;
import com.eventsphere.backend.reservations.service.ReservationService;
import com.eventsphere.backend.tickets.entity.TicketCategory;
import com.eventsphere.backend.tickets.repository.TicketCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the reservation flow using Testcontainers (PostgreSQL + Redis).
 *
 * <p>Covers:
 * <ul>
 *   <li>Concurrent reservation requests never oversell inventory.</li>
 *   <li>Expiry helper atomically releases inventory after TTL.</li>
 *   <li>Redis-down path returns 503 via ServiceUnavailableException.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReservationIntegrationTest {

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private TicketCategoryRepository ticketCategoryRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationExpireHelper expireHelper;

    private User user;
    private Event event;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        ticketCategoryRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .email("it-user-" + UUID.randomUUID() + "@test.com")
                .password("irrelevant").fullName("IT User").role(Role.USER).build());

        User organizer = userRepository.save(User.builder()
                .email("org-" + UUID.randomUUID() + "@test.com")
                .password("irrelevant").fullName("Organizer").role(Role.ORGANIZER).build());

        event = eventRepository.save(Event.builder()
                .organizer(organizer).title("IT Concert")
                .location("Stage").eventDate(LocalDateTime.now().plusDays(30)).build());
    }

    // ── Concurrent reservation race ─────────────────────────────────────────

    /**
     * 10 threads all try to reserve 1 ticket from a category with only 5 available.
     * Each thread retries up to 15 times on lock-held / optimistic-lock conflicts
     * (simulating realistic client retry behaviour), stopping only on
     * "insufficient inventory" (genuine stock exhaustion).
     *
     * <p>Invariant: {@code available_inventory} must never go below 0 (no oversell).
     */
    @Test
    void concurrentReservations_neverOversellInventory() throws InterruptedException {
        int available = 5;
        int threads   = 10;

        TicketCategory category = ticketCategoryRepository.save(TicketCategory.builder()
                .event(event).name("Concurrency-Test")
                .price(BigDecimal.TEN).totalInventory(available).availableInventory(available)
                .build());

        ExecutorService pool      = Executors.newFixedThreadPool(threads);
        CountDownLatch   ready    = new CountDownLatch(threads);
        CountDownLatch   start    = new CountDownLatch(1);
        AtomicInteger    successes = new AtomicInteger();
        AtomicInteger    exhausted = new AtomicInteger();
        List<Future<?>>  futures   = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                CreateReservationRequest req = new CreateReservationRequest();
                req.setEventId(event.getId());
                req.setCategoryId(category.getId());
                req.setQuantity(1);

                ready.countDown();
                try { start.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

                // Retry on transient lock/optimistic conflicts; stop on real inventory exhaustion
                for (int attempt = 0; attempt < 15; attempt++) {
                    try {
                        reservationService.createReservation(req, user);
                        successes.incrementAndGet();
                        return;
                    } catch (com.eventsphere.backend.common.exception.ConflictException ex) {
                        if (ex.getMessage().contains("Insufficient inventory")) {
                            exhausted.incrementAndGet();
                            return; // genuine stock exhaustion — do not retry
                        }
                        // Transient (lock held or optimistic conflict) — back off and retry
                        try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
                exhausted.incrementAndGet(); // gave up after max retries
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Exactly `available` reservations should have been created
        assertThat(successes.get()).isEqualTo(available);
        assertThat(exhausted.get()).isEqualTo(threads - available);

        // The DB must reflect exactly 0 remaining units (no oversell)
        TicketCategory refreshed = ticketCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(refreshed.getAvailableInventory()).isEqualTo(0);
    }

    // ── Expiry helper releases inventory ────────────────────────────────────

    /**
     * A reservation is manually saved with a past expires_at (simulating an elapsed TTL).
     * After the expiry helper runs, inventory must be restored and status must be EXPIRED.
     */
    @Test
    void expireHelper_releasesInventory_andSetsStatusExpired() {
        TicketCategory category = ticketCategoryRepository.save(TicketCategory.builder()
                .event(event).name("Expiry-Test")
                .price(BigDecimal.TEN).totalInventory(10).availableInventory(8) // 2 reserved
                .build());

        Reservation expired = reservationRepository.save(Reservation.builder()
                .user(user).event(event).ticketCategory(category)
                .quantity(2).status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusSeconds(1)) // already in the past
                .build());

        // Run the idempotent expiry logic
        expireHelper.expireOne(expired.getId());

        // Status must be EXPIRED
        Reservation updated = reservationRepository.findById(expired.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // Inventory must be restored: 8 reserved + 2 released = 10
        TicketCategory refreshed = ticketCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(refreshed.getAvailableInventory()).isEqualTo(10);
    }

    @Test
    void expireHelper_isIdempotent_whenCalledTwiceOnSameReservation() {
        TicketCategory category = ticketCategoryRepository.save(TicketCategory.builder()
                .event(event).name("Idempotency-Test")
                .price(BigDecimal.TEN).totalInventory(10).availableInventory(8)
                .build());

        Reservation expired = reservationRepository.save(Reservation.builder()
                .user(user).event(event).ticketCategory(category)
                .quantity(2).status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build());

        expireHelper.expireOne(expired.getId()); // first call — expires + releases
        expireHelper.expireOne(expired.getId()); // second call — should be a no-op

        // Inventory must still be 10, not 12 (no double-release)
        TicketCategory refreshed = ticketCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(refreshed.getAvailableInventory()).isEqualTo(10);
    }

    // ── Redis-unavailable → 503 ──────────────────────────────────────────────

    /**
     * Verifies that when Redis is unavailable, RedisLockService propagates
     * ServiceUnavailableException (which the global handler maps to HTTP 503).
     */
    @Test
    void redisLockService_throws503_whenRedisIsDown() {
        // Inject a broken template that throws on connection
        StringRedisTemplate brokenTemplate = new StringRedisTemplate() {
            @Override
            public org.springframework.data.redis.core.ValueOperations<String, String> opsForValue() {
                throw new RedisConnectionFailureException("Simulated Redis failure");
            }
        };
        RedisLockService serviceUnderTest = new RedisLockService(brokenTemplate);

        assertThatThrownBy(() -> serviceUnderTest.tryLock("test-key", "test-value", 10))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Redis");
    }
}
