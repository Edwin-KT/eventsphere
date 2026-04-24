package com.eventsphere.backend.orders.repository;

import com.eventsphere.backend.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
