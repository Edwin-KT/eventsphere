-- Users
CREATE TABLE users (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email       VARCHAR(255) NOT NULL UNIQUE,
                       password    VARCHAR(255) NOT NULL,
                       full_name   VARCHAR(255) NOT NULL,
                       role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
                       created_at  TIMESTAMP    NOT NULL DEFAULT now(),
                       updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
                                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                token_hash  VARCHAR(255) NOT NULL UNIQUE,
                                user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                expires_at  TIMESTAMP    NOT NULL,
                                revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- Events
CREATE TABLE events (
                        id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        organizer_id UUID         NOT NULL REFERENCES users(id),
                        title        VARCHAR(255) NOT NULL,
                        description  TEXT,
                        location     VARCHAR(255) NOT NULL,
                        event_date   TIMESTAMP    NOT NULL,
                        image_url    VARCHAR(500),
                        status       VARCHAR(50)  NOT NULL DEFAULT 'PUBLISHED',
                        created_at   TIMESTAMP    NOT NULL DEFAULT now(),
                        updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Ticket categories (VIP, General, Early Bird, etc.)
CREATE TABLE ticket_categories (
                                   id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                   event_id            UUID           NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                                   name                VARCHAR(100)   NOT NULL,
                                   price               NUMERIC(10, 2) NOT NULL,
                                   total_inventory     INT            NOT NULL,
                                   available_inventory INT            NOT NULL,
                                   version             BIGINT         NOT NULL DEFAULT 0,
                                   created_at          TIMESTAMP      NOT NULL DEFAULT now(),
                                   updated_at          TIMESTAMP      NOT NULL DEFAULT now()
);

-- Orders
CREATE TABLE orders (
                        id                       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id                  UUID           NOT NULL REFERENCES users(id),
                        status                   VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
                        idempotency_key          VARCHAR(255)   NOT NULL UNIQUE,
                        stripe_payment_intent_id VARCHAR(255),
                        created_at               TIMESTAMP      NOT NULL DEFAULT now(),
                        updated_at               TIMESTAMP      NOT NULL DEFAULT now()
);

-- Tickets
CREATE TABLE tickets (
                         id                 UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                         order_id           UUID      NOT NULL REFERENCES orders(id),
                         event_id           UUID      NOT NULL REFERENCES events(id),
                         ticket_category_id UUID      NOT NULL REFERENCES ticket_categories(id),
                         status             VARCHAR(50) NOT NULL DEFAULT 'VALID',
                         qr_code_hash       VARCHAR(255) NOT NULL UNIQUE,
                         pdf_generated      BOOLEAN   NOT NULL DEFAULT FALSE,
                         cancelled_by       UUID      REFERENCES users(id),
                         cancelled_at       TIMESTAMP,
                         created_at         TIMESTAMP NOT NULL DEFAULT now(),
                         updated_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- Reservations (temporary, TTL 10 minutes enforced in app)
CREATE TABLE reservations (
                              id                 UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id            UUID      NOT NULL REFERENCES users(id),
                              ticket_category_id UUID      NOT NULL REFERENCES ticket_categories(id),
                              quantity           INT       NOT NULL,
                              status             VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                              expires_at         TIMESTAMP NOT NULL,
                              created_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- Waitlist
CREATE TABLE waitlist (
                          id                 UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                          event_id           UUID      NOT NULL REFERENCES events(id),
                          ticket_category_id UUID      NOT NULL REFERENCES ticket_categories(id),
                          user_id            UUID      NOT NULL REFERENCES users(id),
                          created_at         TIMESTAMP NOT NULL DEFAULT now(),
                          UNIQUE (ticket_category_id, user_id)
);

-- Outbox events (Transactional Outbox Pattern)
CREATE TABLE outbox_events (
                               id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id   VARCHAR(255) NOT NULL,
                               event_type     VARCHAR(100) NOT NULL,
                               payload        JSONB        NOT NULL,
                               processed      BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_events_organizer_id       ON events(organizer_id);
CREATE INDEX idx_events_event_date         ON events(event_date);
CREATE INDEX idx_ticket_categories_event   ON ticket_categories(event_id);
CREATE INDEX idx_orders_user_id            ON orders(user_id);
CREATE INDEX idx_orders_idempotency_key    ON orders(idempotency_key);
CREATE INDEX idx_tickets_order_id          ON tickets(order_id);
CREATE INDEX idx_tickets_event_id          ON tickets(event_id);
CREATE INDEX idx_reservations_user_id      ON reservations(user_id);
CREATE INDEX idx_reservations_expires_at   ON reservations(expires_at);
CREATE INDEX idx_waitlist_event_id         ON waitlist(event_id);
CREATE INDEX idx_outbox_events_processed   ON outbox_events(processed, created_at);