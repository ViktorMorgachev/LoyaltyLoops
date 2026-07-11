# ENGINEERING_CHANGELOG.md

Журнал внедрённых изменений. Новая запись добавляется в начало при каждом шипе.

Формат:

```markdown
## {Дата} — {Название}

### Внедрено
- Что реализовано
- Ключевые технические решения
- Краткий список изменённых файлов
```

---

## 2026-07-09 — TD-020 pass 3: реанимация закомментированных тестов

### Внедрено
- `AuthTest`: раскомментированы и адаптированы под текущий контракт (X-Timezone-Id) три готовых теста — полный OTP-флоу логина, ротация refresh-токена с ревокацией старого, отклонение истёкшего access-токена.
- `PartnerConstraintsTest`: реализованы заглушки — дубликат бизнеса у владельца (409 `BUSINESS_ALREADY_EXISTS`), сброс PIN замораживает аккаунт (последующие мутирующие запросы → 403 `ACCOUNT_FROZEN`); тест попутно покрывает появление workspace после создания партнера.
- `Application.kt`: удалена неиспользуемая инъекция `emailTemplatesService`.
- Отложенные сценарии зафиксированы в TD-020 (registerAsAdmin-хелпер, EmailDebugStore); их код — в git-истории тест-файлов.

---

## 2026-07-09 — Новый скилл loyaltyloops-tests

### Внедрено
- Скилл `loyaltyloops-tests` (актуализация тестов при изменении кода): карта тестовой инфраструктуры (H2, TestUtils, debugCode), workflow «изменился код» и «тест упал» с классификацией «протухший тест vs реальный баг», запрет на комментирование упавших тестов, приоритеты покрытия (деньги → access control → валидация). Уроки сегодняшней реанимации тестов зашиты в правила.
- `CLAUDE.md`: скилл добавлен в Skill Map и в Vibe Coding Loop (шаг 3, перед ревью).

---

## 2026-07-09 — Реанимация тестового слоя (первый заход)

### Внедрено
- `ValidationUtilsTest` переписан под актуальный контракт: `validatePhoneNumber` бросает `LoyaltyException(INVALID_PHONE)`, тесты были написаны под старую сигнатуру `String?` и не могли пройти (`assertNull(Unit)`); дописаны негативные кейсы (короткие номера, неизвестный код, пустая/мусорная строка).
- `LoyaltyCalculatorTest.mathematicalRounding`: ожидание синхронизировано с фикстурой (tier 1 = 1% → 12.35), тест снова проверяет округление до 2 знаков.
- `TestUtils.registerAndLogin`: ошибка send-code теперь включает тело ответа — для диагностики падающего auth-флоу в тестах.
- Выяснено: `PartnerConstraintsTest` — единственный тест, реально выполняющий `registerAndLogin` (в остальных закомментирован). Восстановлен auth-флоу в тестах, по цепочке найдены и исправлены: (1) хелпер не слал обязательный `X-Timezone-Id` — теперь `Asia/Bishkek` в пару к KG-номерам; (2) баг сервера: `ConsoleSmsService.startVerification` возвращал телефон вместо кода — поле `debugCode` в ответе `/auth/send-code` не содержало код, дев-режим/тесты не могли залогиниться; (3) баг сервера: `checkCode` логировал неудачную OTP-попытку с `userPhone = verificationId`, из-за чего `checkOtpBlock` (анти-brute-force) никогда не срабатывал.

---

## 2026-07-09 — TD-020 pass 2: нейминг, структура файлов, generic throw

### Внедрено
- camelCase для приватных полей: `cacheTtlSeconds` (ExchangeRateService), алиасы таблиц в PlatformRepository/RatingRepository, локализационные map в TelegramAuthService (13 переименований, все private).
- `ExchangeRateService`: `error(...)` вместо `throw Exception(...)` (2), отдельный `catch (e: CancellationException)` вместо instanceof-проверки, `cause` в LoyaltyException при неудачном фетче курса.
- Файлы приведены к именам деклараций: `LoyaltyCardsTable.kt`, `AuthSessionStatus.kt`; `WaitlistRequest` вынесен из PublicRoutes в `models/`.
- `TestUtils`: `error(...)` вместо `throw IllegalStateException` (2); EOF-newline в 2 тестах.
- WildcardImport 80/80 устранены (IDE Optimize Imports, single-name policy закреплена).

---

## 2026-07-07 — TD-020 (частично): проглоченные исключения и мёртвый код

### Внедрено
- `LoyaltyException` принимает `cause` — оригинальные исключения больше не теряются (`StringExt.toUUID`, `PartnerRepository`, `ClientRoutes`, `PartnerRoutes`).
- Все 14 `SwallowedException` устранены: осознанные фоллбэки (ZoneId→UTC, UUID→null, WS-disconnect) помечены `catch (_: ...)` по конвенции detekt; в `MapsRoutes` тихое `emptyList` при ошибке БД теперь логируется `warn`.
- Удалён мёртвый код: константы анти-абьюза в `RatingService` (логика давно в конфиге/репозитории), параметры `viewerIsPartner` (SupportChatRepository), `userId`/`role` в проверках SystemStaffRepository, `partnerRepository` в mapsRoutes, неиспользуемый логгер AuthSessionRepository.
- Удалён дубль baseline (`server/detekt-baseline.xml`); действующий — `config/detekt/baseline-server.xml`.
- Изменённые файлы: 12 файлов server (routes, repositories, services, utils), `Application.kt`.

---

## 2026-07-07 — TD-017/TD-018: детерминированная Docker-сборка сервера

### Внедрено
- Образ сборки поднят до `gradle:8.7.0-jdk17` (версия совпадает с `gradle-wrapper.properties`), сборка через предустановленный `gradle` вместо `./gradlew` — wrapper больше не качает дистрибутив на каждом деплое (причина падения 2026-07-07 устранена).
- `GRADLE_OPTS=-Xmx3g -XX:MaxMetaspaceSize=512m` в Dockerfile — переопределение локального `-Xmx16g`, защита от OOM на CI.
- `networkTimeout` wrapper'а поднят до 120 сек (подстраховка для локальных/CI сборок через wrapper).
- Изменённые файлы: `Dockerfile`, `gradle/wrapper/gradle-wrapper.properties`, `docs/TECH_DEBT.md`.

---

## 2026-07-07 — Detekt baseline: разблокирован :server:build

### Внедрено
- В detekt-конфиг всех модулей добавлен `baseline = config/detekt/baseline-{module}.xml`: легаси-замечания (~400) замораживаются, новый код проверяется строго. Генерация: `./gradlew detektBaseline` (закоммитить полученные файлы).
- Исправлено безопасно-механическое: 43 файла получили newline в конце (server/shared); `@Suppress("SpreadOperator")` в `DatabaseFactory` (вызов один раз на старте).
- Разбор замороженного долга зафиксирован как TD-020 (приоритет — SwallowedException и Unused*).

---

## 2026-07-07 — TD-004: Flyway-миграции вместо createMissingTablesAndColumns

### Внедрено
- Flyway 9.22.3 (`libs.flyway.core`). Миграции: `server/src/main/resources/db/migration/`.
- `V1__baseline.sql` — полная текущая схема (23 таблицы), выполняется только на пустых БД; существующие БД помечаются версией 1 через `baselineOnMigrate=true`.
- `V2__loyalty_cards_balance_check.sql` — CHECK `balance >= 0 NOT VALID` (страховка к TD-003, применится и на проде без скана существующих строк).
- `DatabaseFactory.init`: Postgres → Flyway; не-Postgres (H2 в тестах) → прежний `createMissingTablesAndColumns` (миграции Postgres-специфичны).
- Новое правило разработки: изменение схемы = table object + миграция `V{N}` (CLAUDE.md, ENGINEERING_NOTES, скиллы обновлены).
- Изменённые файлы: `gradle/libs.versions.toml`, `server/build.gradle.kts`, `DatabaseFactory.kt`, `db/migration/V1..V2`, `CLAUDE.md`, `docs/**`.

---

## 2026-07-07 — TD-003: сериализация денежных операций по карте

### Внедрено
- `TransactionService.processTransaction` первым шагом блокирует строку карты (`SELECT ... FOR UPDATE` через `LoyaltyCardRepository.lockCardRow`) — параллельные транзакции по одной карте выполняются последовательно, проверка баланса идёт по актуальному значению.
- Второй слой защиты: в `addCashback` списание выполняется условным UPDATE с `balance >= сумма`; при гонке или недостатке средств — `LoyaltyException(INVALID_AMOUNT, "Insufficient balance")` и откат транзакции.
- Конкурентный тест зафиксирован отдельно как TD-019.
- Изменённые файлы: `TransactionService.kt`, `LoyaltyCardRepository.kt`, `docs/TECH_DEBT.md`.

---

## 2026-07-07 — Fix: .dockerignore ломал сборку web-admin

### Внедрено
- Из корневого `.dockerignore` убрано исключение `web-admin`: Railway собирает `web-admin/Dockerfile` с корневым build context, и `COPY web-admin/...` падал с «not found». Вместо каталога целиком исключён только `web-admin/dist`; `node_modules` покрыт паттерном `**/node_modules`.
- Урок: один `.dockerignore` обслуживает все Dockerfile'ы, собирающиеся из корня, — проверять надо оба образа (server и web-admin).

---

## 2026-07-07 — TD-002: CORS whitelist вместо anyHost()

### Внедрено
- `anyHost()` в CORS заменён на явный whitelist из конфига: `cors.allowedHosts` в `application.conf`, override через env `CORS_ALLOWED_HOSTS` (хосты через запятую, без схемы).
- Дефолтный список: `loyaltyloops.app`, `www.loyaltyloops.app` (web prod), `loyalityloop-beta.up.railway.app` (web stage), `localhost:3000` (Vite dev).
- Мобильные клиенты не затронуты: нативные приложения не отправляют заголовок Origin, плагин CORS ограничивает только браузерные запросы. WebSocket-подключения web-admin с разрешённых доменов также проходят.
- Изменённые файлы: `server/src/main/kotlin/io/loyaltyloop/server/Application.kt`, `server/src/main/resources/application.conf`, `docs/TECH_DEBT.md`.

---

## 2026-07-07 — Стандарт ведения проекта

### Внедрено.
- Добавлен `.dockerignore` (раньше в build context уходили `.git`, `node_modules`, `iosApp/Pods`).
- Введён стандарт ведения проекта: `CLAUDE.md` + скиллы `loyaltyloops-dev` / `loyaltyloops-review` / `loyaltyloops-docs` / `loyaltyloops-design` (перенесены из Selvor/VoyagePay и адаптированы).
- Реорганизована документация: `docs/flows|operations|testing|tasks|reviews|archive|screens`, созданы `TECH_DEBT.md` (18 записей по итогам аудита), `TECH_STACK.md`, `ENGINEERING_NOTES.md`, `SCREEN_DOCUMENTATION_GUIDE.md`.
- Скиллы переименованы под имя проекта: `loyaltyloops-dev` / `-review` / `-docs` / `-design`.
- Дедупликация документации: `B2B_CRM_ARCHITECTURE.md` + `B2B_CRM_MANAGER_FLOW.md` слиты в `flows/B2B_CRM.md` (trial-логика исправлена: на точку, не на партнёра; API-список сверен с кодом); `AUTOMATED_MODERATION.md` удалён (дубль `flows/RATINGS.md`); из двух RFC мультивалютности оставлен полный (`flows/MULTY_CURRENCY.md`); `TELEGRAM_WEBHOOK.md` влит в `operations/TELEGRAM_BOT_SETUP.md`.
- Актуализация по коду: `LOYALTY_FLOW.md` — эндпоинты терминала исправлены на `POST /terminal/scan|process`; `TELEGRAM_AUTH_FLOW.md` — polling заменён на webhook; `RATINGS.md` — уточнено условие анти-абьюза (`totalScore > 100`).
- Нереализованные концепты перенесены в планы: `tasks/NOTIFICATIONS_PLAN.md`; `DATABASE_MIGRATIONS.md` переименован в `operations/POSTGRES_UTC_SETUP.md` (содержание — настройка UTC, не миграции).
- Изменённые файлы: `.dockerignore`, `.gitignore`, `README.md`, `CLAUDE.md`, `.claude/skills/*`, `docs/**`.
