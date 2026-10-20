// Відкриття продажу на популярну подію: усі хочуть перші ряди.
//
//  buyers   — покупці без пауз намагаються зарезервувати по 2 сусідні місця; вибір сильно зміщений до перших рядів,
//             тож після перших секунд більшість спроб — конфлікти (409). Саме тут Redis відсіює зайняті місця
//             до транзакції в Postgres.
//  browsers — фіксований потік переглядів карти місць (кешується в Redis на 3 с).
//
// Спершу WARMUP_SEC секунд те саме навантаження йде на окрему подію: холодний JVM (JIT, пули з'єднань) у кілька разів
// повільніший, і без прогріву перший прогін нечесно програє другому. У звіт потрапляє лише основна фаза.
//
// Наприкінці скрипт перевіряє головне: зайнятих місць рівно 2 × успішних замовлень, тобто подвійних продажів немає.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import exec from 'k6/execution';

const API = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1`;
const LABEL = __ENV.LABEL || 'run';
const BUYERS = Number(__ENV.BUYERS || 100);
const DURATION_SEC = Number(__ENV.DURATION_SEC || 30);
const WARMUP_SEC = Number(__ENV.WARMUP_SEC || 45);
const MAP_RATE = Number(__ENV.MAP_RATE || 100); // переглядів карти за секунду
const ROWS = 20;
const SEATS_PER_ROW = 50;
const SEATS_PER_ORDER = 2;

const ordersCreated = new Counter('orders_created');
const ordersRejected = new Counter('orders_rejected');
const seatsTaken = new Gauge('seats_taken');

// 409 — штатна відповідь «місце зайняте», а не збій
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

export const options = {
    setupTimeout: '5m',
    scenarios: {
        warmup_buyers: buyers(0, WARMUP_SEC),
        warmup_browsers: browsers(0, WARMUP_SEC),
        buyers: buyers(WARMUP_SEC, DURATION_SEC),
        browsers: browsers(WARMUP_SEC, DURATION_SEC),
    },
    // пороги лише на запити сценаріїв — setup (очікування старту, реєстрація) сюди не входить;
    // заодно k6 рахує ці підметрики і віддає їх у handleSummary
    thresholds: {
        'http_req_failed{scenario:buyers}': ['rate<0.01'],
        'http_req_failed{scenario:browsers}': ['rate<0.01'],
        'http_req_duration{name:POST /orders}': ['p(95)<1000'],
        'http_req_duration{name:GET /events/{id}/seats}': ['p(95)<1000'],
        'http_reqs{name:GET /events/{id}/seats}': ['count>0'],
        checks: ['rate>0.99'],
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    waitUntilHealthy();
    const admin = login('admin@tickethub.local');
    const organizer = login('organizer@tickethub.local');

    const venue = send('POST', '/venues', admin, {
        name: `Load Hall ${Date.now()}`, city: 'Київ', address: 'вул. Навантажувальна, 1',
        sections: [{ name: 'Партер', rowsCount: ROWS, seatsPerRow: SEATS_PER_ROW }],
    });
    return {
        warmup: publishEvent(organizer, venue, `Прогрів ${LABEL}`, 30),
        main: publishEvent(organizer, venue, `Розпродаж ${LABEL}`, 31),
        tokens: registerBuyers(BUYERS),
    };
}

export function buy(data) {
    const { event, warmup } = phase(data);
    const token = data.tokens[(exec.vu.idInTest - 1) % data.tokens.length];
    // куб від рівномірного розподілу: ~50% спроб ідуть у перші 12% місць
    const first = Math.min(Math.floor(Math.random() ** 3 * event.seats.length), event.seats.length - SEATS_PER_ORDER);
    const res = http.post(`${API}/orders`, JSON.stringify({
        eventId: event.id,
        eventSeatIds: event.seats.slice(first, first + SEATS_PER_ORDER),
    }), {
        headers: { 'Content-Type': 'application/json', Authorization: token, 'Idempotency-Key': crypto.randomUUID() },
        tags: { name: warmup ? 'warmup' : 'POST /orders' },
    });
    check(res, { 'order: 201 або 409': (r) => r.status === 201 || r.status === 409 });
    if (warmup) return;
    if (res.status === 201) ordersCreated.add(1);
    if (res.status === 409) ordersRejected.add(1);
}

export function browse(data) {
    const { event, warmup } = phase(data);
    const res = http.get(`${API}/events/${event.id}/seats`, { tags: { name: warmup ? 'warmup' : 'GET /events/{id}/seats' } });
    check(res, { 'seat map: 200': (r) => r.status === 200 });
}

export function teardown(data) {
    sleep(4); // карта місць кешується на 3 с — читаємо вже свіжу
    const taken = send('GET', `/events/${data.main.id}/seats`).filter((seat) => seat.status !== 'AVAILABLE');
    seatsTaken.add(taken.length);
}

export function handleSummary(data) {
    const metric = (name, stat) => data.metrics[name]?.values[stat] ?? 0;
    const trend = (name) => {
        const key = `http_req_duration{name:${name}}`;
        return data.metrics[key]
            ? `p50 ${fmt(metric(key, 'med'))} мс · p95 ${fmt(metric(key, 'p(95)'))} мс · p99 ${fmt(metric(key, 'p(99)'))} мс`
            : 'немає даних';
    };
    const pct = (name) => `${fmt(metric(name, 'rate') * 100)}%`;
    const created = metric('orders_created', 'count');
    const rejected = metric('orders_rejected', 'count');
    const taken = metric('seats_taken', 'value');
    const verdict = created + rejected === 0
        ? 'прогін не відбувся (див. помилку setup вище)'
        : taken === created * SEATS_PER_ORDER
        ? `OK — зайнято ${taken} місць = 2 × ${created} замовлень, подвійних продажів немає`
        : `ПОМИЛКА — зайнято ${taken} місць, а замовлень ${created}`;

    const report = [
        `\n=== TicketHub booking rush [${LABEL}] ===`,
        `POST /orders       ${fmt((created + rejected) / DURATION_SEC)} запитів/с · ${trend('POST /orders')}`,
        `  створено ${created}, відхилено 409: ${rejected}`,
        `GET  карта місць   ${fmt(metric('http_reqs{name:GET /events/{id}/seats}', 'count') / DURATION_SEC)} запитів/с · ${trend('GET /events/{id}/seats')}`,
        `Помилки (не 2xx/409): замовлення ${pct('http_req_failed{scenario:buyers}')}, карта ${pct('http_req_failed{scenario:browsers}')}`,
        `Перевірка: ${verdict}`,
        '',
    ].join('\n');

    return {
        stdout: report,
        [`results/${LABEL}.json`]: JSON.stringify(data, null, 2),
    };
}

function buyers(startSec, durationSec) {
    return { executor: 'constant-vus', exec: 'buy', vus: BUYERS, startTime: `${startSec}s`, duration: `${durationSec}s` };
}

function browsers(startSec, durationSec) {
    return {
        executor: 'constant-arrival-rate', exec: 'browse', rate: MAP_RATE, timeUnit: '1s',
        startTime: `${startSec}s`, duration: `${durationSec}s`, preAllocatedVUs: 50, maxVUs: 200,
    };
}

function phase(data) {
    const warmup = exec.scenario.name.startsWith('warmup');
    return { event: warmup ? data.warmup : data.main, warmup };
}

function publishEvent(organizer, venue, title, daysAhead) {
    const startsAt = new Date(Date.now() + daysAhead * 24 * 3600 * 1000);
    const event = send('POST', '/events', organizer, {
        venueId: venue.id, title,
        startsAt: startsAt.toISOString(), endsAt: new Date(startsAt.getTime() + 2 * 3600 * 1000).toISOString(),
    });
    send('POST', `/events/${event.id}/price-zones`, organizer, {
        zones: [{ name: 'Партер', price: 500, currency: 'UAH', seats: [{ sectionId: venue.sections[0].id }] }],
    });
    send('POST', `/events/${event.id}/publish`, organizer);
    return { id: event.id, seats: send('GET', `/events/${event.id}/seats`).map((seat) => seat.id) };
}

function fmt(value) {
    return Number(value).toFixed(1);
}

function send(method, path, token, body) {
    const params = { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } };
    if (token) params.headers.Authorization = token;
    const res = http.request(method, `${API}${path}`, body === undefined ? null : JSON.stringify(body), params);
    if (res.status < 200 || res.status >= 300) {
        throw new Error(`${method} ${path} → ${res.status}: ${res.body}`);
    }
    return res.body ? res.json() : null;
}

function login(email) {
    return `Bearer ${send('POST', '/auth/login', null, { email, password: 'password' }).accessToken}`;
}

function registerBuyers(count) {
    const tokens = [];
    const run = Date.now();
    for (let offset = 0; offset < count; offset += 20) {
        const batch = [];
        for (let i = offset; i < Math.min(offset + 20, count); i++) {
            batch.push(['POST', `${API}/auth/register`, JSON.stringify({
                email: `buyer-${run}-${i}@load.local`, password: 'password123', fullName: `Buyer ${i}`,
            }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } }]);
        }
        for (const res of http.batch(batch)) {
            if (res.status !== 201) throw new Error(`register → ${res.status}: ${res.body}`);
            tokens.push(`Bearer ${res.json().accessToken}`);
        }
    }
    return tokens;
}

function waitUntilHealthy() {
    for (let attempt = 0; attempt < 90; attempt++) {
        const res = http.get(`${API.replace('/api/v1', '')}/actuator/health`, { tags: { name: 'setup' } });
        if (res.status === 200) return;
        sleep(2);
    }
    throw new Error('застосунок не піднявся за 3 хвилини');
}
