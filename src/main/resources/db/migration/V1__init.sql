CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER','ORGANIZER','ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sections (
    id         UUID PRIMARY KEY,
    venue_id   UUID         NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    rows_count INT          NOT NULL CHECK (rows_count > 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (venue_id, name)
);

CREATE TABLE seats (
    id          UUID PRIMARY KEY,
    section_id  UUID        NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    row_number  INT         NOT NULL CHECK (row_number > 0),
    seat_number INT         NOT NULL CHECK (seat_number > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (section_id, row_number, seat_number)
);

CREATE TABLE events (
    id           UUID PRIMARY KEY,
    venue_id     UUID         NOT NULL REFERENCES venues(id),
    organizer_id UUID         NOT NULL REFERENCES users(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED','FINISHED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_events_status_starts ON events(status, starts_at);
CREATE INDEX idx_events_organizer ON events(organizer_id);

CREATE TABLE price_zones (
    id         UUID PRIMARY KEY,
    event_id   UUID          NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name       VARCHAR(100)  NOT NULL,
    price      NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    currency   CHAR(3)       NOT NULL DEFAULT 'UAH',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (event_id, name)
);

CREATE TABLE event_seats (
    id             UUID PRIMARY KEY,
    event_id       UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_id        UUID        NOT NULL REFERENCES seats(id),
    price_zone_id  UUID        NOT NULL REFERENCES price_zones(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE','RESERVED','SOLD','BLOCKED')),
    reserved_until TIMESTAMPTZ,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, seat_id)
);
CREATE INDEX idx_event_seats_event_status ON event_seats(event_id, status);
CREATE INDEX idx_event_seats_expiring ON event_seats(reserved_until) WHERE status = 'RESERVED';

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users(id),
    event_id        UUID          NOT NULL REFERENCES events(id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PAID','EXPIRED','CANCELLED','REFUNDED')),
    total_amount    NUMERIC(10,2) NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user ON orders(user_id, created_at DESC);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY,
    order_id      UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_seat_id UUID          NOT NULL REFERENCES event_seats(id),
    price         NUMERIC(10,2) NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- одне місце може бути лише в одному активному замовленні
CREATE UNIQUE INDEX uq_order_items_active_seat ON order_items(event_seat_id) WHERE active;

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    order_id            UUID          NOT NULL UNIQUE REFERENCES orders(id),
    provider_payment_id VARCHAR(100)  NOT NULL UNIQUE,
    amount              NUMERIC(10,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('CREATED','SUCCEEDED','FAILED')),
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id            UUID PRIMARY KEY,
    order_item_id UUID        NOT NULL UNIQUE REFERENCES order_items(id),
    code          VARCHAR(64) NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL DEFAULT 'VALID'
                  CHECK (status IN ('VALID','USED','CANCELLED')),
    pdf_path      VARCHAR(500),
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
