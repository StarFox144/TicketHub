-- Які місця входять до цінової зони: секція цілком (row_from/row_to = NULL) або діапазон рядів.
-- Під час публікації кожне місце залу, покрите зоною, стає рядком event_seats.
CREATE TABLE price_zone_ranges (
    price_zone_id UUID NOT NULL REFERENCES price_zones(id) ON DELETE CASCADE,
    section_id    UUID NOT NULL REFERENCES sections(id),
    row_from      INT CHECK (row_from > 0),
    row_to        INT CHECK (row_to > 0),
    CHECK (row_from IS NULL OR row_to IS NULL OR row_to >= row_from)
);
CREATE INDEX idx_price_zone_ranges_zone ON price_zone_ranges(price_zone_id);

-- Валюта замовлення (усі місця одного замовлення мають бути в одній валюті)
ALTER TABLE orders ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'UAH';

-- ExpiredReservationJob шукає прострочені PENDING-замовлення
CREATE INDEX idx_orders_pending_expiry ON orders(expires_at) WHERE status = 'PENDING';
