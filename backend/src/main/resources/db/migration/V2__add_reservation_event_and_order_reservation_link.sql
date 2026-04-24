-- Add event_id to reservations so the API body { eventId, categoryId, quantity } can be persisted.
-- Nullable to avoid failures if the table already contains rows; the application always sets this
-- field for every new reservation.
ALTER TABLE reservations
    ADD COLUMN event_id UUID REFERENCES events(id);

-- Link orders to the reservation they fulfil
ALTER TABLE orders
    ADD COLUMN reservation_id UUID REFERENCES reservations(id);

-- The spec requires idempotency scoped per user, not globally.
-- Drop the global unique index created by the inline UNIQUE in V1 and replace with a
-- composite (user_id, idempotency_key) unique constraint.
ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_idempotency_key_key;

ALTER TABLE orders
    ADD CONSTRAINT orders_user_idempotency_unique UNIQUE (user_id, idempotency_key);

-- Composite index used by the expiry scheduler query (status + expires_at)
CREATE INDEX IF NOT EXISTS idx_reservations_status_expires
    ON reservations (status, expires_at);
