-- Transactional outbox: подія пишеться в одній транзакції зі зміною стану (Order → PAID),
-- а relay окремо доставляє її в RabbitMQ. Брокер, недоступний у момент коміту, не губить подію.
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

ALTER TABLE payments ADD COLUMN currency    CHAR(3) NOT NULL DEFAULT 'UAH';
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMPTZ;

-- Денормалізація для запитів «мої квитки» і перевірки на вході без join через чужі модулі
ALTER TABLE tickets ADD COLUMN order_id      UUID NOT NULL REFERENCES orders(id);
ALTER TABLE tickets ADD COLUMN user_id       UUID NOT NULL REFERENCES users(id);
ALTER TABLE tickets ADD COLUMN event_id      UUID NOT NULL REFERENCES events(id);
ALTER TABLE tickets ADD COLUMN event_seat_id UUID NOT NULL REFERENCES event_seats(id);
CREATE INDEX idx_tickets_user  ON tickets(user_id, created_at DESC);
CREATE INDEX idx_tickets_order ON tickets(order_id);
