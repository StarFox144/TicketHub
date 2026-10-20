# TicketHub

Бекенд сервісу продажу квитків на події з вибором конкретного місця в залі.
Головна інженерна задача — коректний продаж при одночасних покупках: одне місце ніколи не продається двічі.

**Стек:** Java 21 · Spring Boot 3.5 · Spring Security + JWT (jjwt) · PostgreSQL 16 · Flyway · MapStruct · springdoc-openapi · JUnit 5 + Testcontainers.
Далі за планом: Redis, RabbitMQ, OpenPDF + ZXing, Mailpit.

## Запуск

Потрібні Docker і JDK 17+ для запуску Gradle (JDK 21 для компіляції Gradle завантажить сам через toolchains).

```bash
docker compose up -d postgres redis rabbitmq mailpit   # інфраструктура
./gradlew bootRun --args='--spring.profiles.active=local'
```

Або все разом, включно із застосунком у контейнері: `docker compose up --build`.

| Що | Адреса |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ консоль | http://localhost:15672 (guest / guest) |
| Mailpit | http://localhost:8025 |

Профіль `local` додає демо-дані (`db/seed/R__seed_demo.sql`): зал «Палац мистецтв» на 200 місць,
одну опубліковану подію і користувачів (пароль у всіх — `password`):

| Email | Роль |
|---|---|
| `admin@tickethub.local` | ADMIN |
| `organizer@tickethub.local` | ORGANIZER |
| `user@tickethub.local` | USER |

Поза профілем `local` застосунок не стартує без секрету JWT: задайте `TICKETHUB_SECURITY_JWT_SECRET`
(base64, щонайменше 256 біт, наприклад `openssl rand -base64 64`).

## Автентифікація

```bash
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"organizer@tickethub.local","password":"password"}'
# → { "tokenType": "Bearer", "accessToken": "...", "accessTokenExpiresAt": "...", "refreshToken": "..." }
```

- **Access-токен** — JWT (HS512), живе 15 хв, передається як `Authorization: Bearer ...`. Перевіряється без звернення до БД.
- **Refresh-токен** — випадковий непрозорий рядок, живе 7 днів, у БД зберігається лише його SHA-256 хеш.
  Кожен `POST /auth/refresh` погашає старий токен і видає нову пару (ротація). Повторне використання вже
  погашеного токена вважається крадіжкою: відкликаються всі сесії користувача.
- **Ролі:** самореєстрація завжди дає `USER`; `ORGANIZER`/`ADMIN` призначає адміністратор (`PUT /users/{id}/role`).
- **Власник події** перевіряється через `@PreAuthorize("hasRole('ORGANIZER') and @eventSecurity.isOwner(#id, authentication)")`.
  Неіснуюча подія дає 404, чужа — 403; чернетки для сторонніх не існують (404).
- **Блокування** (`POST /users/{id}/block`) відкликає всі refresh-токени; уже виданий access-токен доживає свої ≤15 хв —
  свідомий компроміс stateless-підходу.

## Тести

```bash
./gradlew test
```

Інтеграційні тести (`*IT`) піднімають справжній PostgreSQL через Testcontainers, тому потрібен запущений Docker.

## Структура

Модульний моноліт, package-by-feature: `common`, `user`, `venue`, `event`, далі — `booking`, `payment`, `ticket`.
Кожен модуль має `api`, `domain`, `repository`, `service`. Модулі звертаються один до одного лише через сервіси,
ніколи через чужі репозиторії.

Ключове рішення моделі: фізичне місце в залі (`Seat`) і місце на конкретну подію (`EventSeat`) — різні сутності.
Цілісність захищена на рівні БД: `UNIQUE (event_id, seat_id)` і частковий унікальний індекс
`order_items(event_seat_id) WHERE active`.

## План

- [x] **Етап 1. Каркас і каталог** — Flyway-схема, зали з генерацією місць, CRUD подій, цінові зони, каталог з фільтрами, RFC 7807, Swagger, Testcontainers
- [x] **Етап 2. Користувачі й безпека** — реєстрація, JWT + refresh, ролі, перевірка власника
- [ ] **Етап 3. Бронювання** — `EventSeat`, публікація, `POST /orders` з песимістичним блокуванням, ідемпотентність, протухання резервів, тест на 50 потоків
- [ ] **Етап 4. Redis** — `SET NX EX`, кеш карти місць, rate limiting
- [ ] **Етап 5. Оплата і квитки** — фейковий провайдер, HMAC-webhook, outbox, RabbitMQ, QR + PDF, email
- [ ] **Етап 6. Полірування** — JaCoCo, ArchUnit, k6, діаграма архітектури
