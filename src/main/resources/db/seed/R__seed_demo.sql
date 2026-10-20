-- Демо-дані для профілю local. Ідемпотентно: фіксовані id + ON CONFLICT DO NOTHING.
-- Пароль усіх демо-користувачів: password

INSERT INTO users (id, email, password_hash, full_name, role) VALUES
    ('00000000-0000-0000-0000-000000000001', 'organizer@tickethub.local',
     '$2a$10$iwoMbyMpnGaN6zIMwZJ9SezE4j9ch20UQpCzRevT54DE02BesmThi', 'Demo Organizer', 'ORGANIZER'),
    ('00000000-0000-0000-0000-000000000002', 'user@tickethub.local',
     '$2a$10$iwoMbyMpnGaN6zIMwZJ9SezE4j9ch20UQpCzRevT54DE02BesmThi', 'Demo User', 'USER'),
    ('00000000-0000-0000-0000-000000000003', 'admin@tickethub.local',
     '$2a$10$iwoMbyMpnGaN6zIMwZJ9SezE4j9ch20UQpCzRevT54DE02BesmThi', 'Demo Admin', 'ADMIN')
ON CONFLICT (id) DO NOTHING;

INSERT INTO venues (id, name, city, address) VALUES
    ('00000000-0000-0000-0001-000000000001', 'Палац мистецтв', 'Київ', 'вул. Хрещатик, 1')
ON CONFLICT (id) DO NOTHING;

-- Партер 10×15 = 150 місць, Балкон 5×10 = 50 місць, разом 200
INSERT INTO sections (id, venue_id, name, rows_count) VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0001-000000000001', 'Партер', 10),
    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0001-000000000001', 'Балкон', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO seats (id, section_id, row_number, seat_number)
SELECT md5('seat:' || sec.id || ':' || r || ':' || s)::uuid, sec.id, r, s
FROM (VALUES ('00000000-0000-0000-0002-000000000001'::uuid, 10, 15),
             ('00000000-0000-0000-0002-000000000002'::uuid, 5, 10)) AS sec(id, rows_count, per_row)
CROSS JOIN LATERAL generate_series(1, sec.rows_count) AS r
CROSS JOIN LATERAL generate_series(1, sec.per_row) AS s
ON CONFLICT (section_id, row_number, seat_number) DO NOTHING;

INSERT INTO events (id, venue_id, organizer_id, title, description, starts_at, ends_at, status) VALUES
    ('00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0001-000000000001',
     '00000000-0000-0000-0000-000000000001', 'Джазовий вечір', 'Демо-подія для локальної розробки',
     '2027-03-01 18:00:00+02', '2027-03-01 21:00:00+02', 'PUBLISHED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO price_zones (id, event_id, name, price, currency) VALUES
    ('00000000-0000-0000-0004-000000000001', '00000000-0000-0000-0003-000000000001', 'VIP', 1200.00, 'UAH'),
    ('00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0003-000000000001', 'Стандарт', 600.00, 'UAH'),
    ('00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0003-000000000001', 'Балкон', 400.00, 'UAH')
ON CONFLICT (id) DO NOTHING;

-- VIP — перші 3 ряди партеру, Стандарт — решта партеру, Балкон — балкон
INSERT INTO event_seats (id, event_id, seat_id, price_zone_id)
SELECT md5('event-seat:00000000-0000-0000-0003-000000000001:' || st.id)::uuid,
       '00000000-0000-0000-0003-000000000001',
       st.id,
       CASE
           WHEN st.section_id = '00000000-0000-0000-0002-000000000002' THEN '00000000-0000-0000-0004-000000000003'::uuid
           WHEN st.row_number <= 3 THEN '00000000-0000-0000-0004-000000000001'::uuid
           ELSE '00000000-0000-0000-0004-000000000002'::uuid
       END
FROM seats st
JOIN sections sec ON sec.id = st.section_id
WHERE sec.venue_id = '00000000-0000-0000-0001-000000000001'
ON CONFLICT (event_id, seat_id) DO NOTHING;
