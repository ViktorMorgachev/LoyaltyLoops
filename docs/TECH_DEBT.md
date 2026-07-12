# TECH_DEBT.md

## Назначение

Документ фиксирует текущий технический долг проекта LoyaltyLoops.

Здесь хранятся:
- известные архитектурные компромиссы;
- временные решения;
- места, которые нужно усилить или упростить позже;
- технические задачи, которые не входят в текущий feature scope, но уже выявлены.

Важно:
- этот документ не заменяет roadmap;
- сюда не попадают продуктовые хотелки;
- сюда попадает только реальный технический долг, который уже возник в коде, инфраструктуре или data flow.

Правила работы:
- **Читать перед каждой фичей.** Если в зоне фичи есть открытый TD — либо чинить попутно (отдельным коммитом), либо явно фиксировать в task-документе, что не трогаем и почему.
- **Обновлять** при закрытии TD: статус `~~TD-N~~ ✅ ЗАКРЫТ` + дата + ссылка на ENGINEERING_CHANGELOG.

## Формат записи

- `ID`, `Область`, `Проблема`, `Почему это техдолг`, `Риск`, `Приоритет`, `Что нужно сделать`, `Когда делать`

---

# Текущий технический долг

## TD-001 — JWT-секрет с дефолтом «пустая строка»

- **Область:** server / безопасность
- **Проблема:** в `Application.kt` `jwt.secret` читается с дефолтом `""`, и `Algorithm.HMAC256("")` молча работает. `TokenService` при этом читает тот же ключ без дефолта — поведение несогласовано.
- **Почему это техдолг:** отсутствие env-переменной на проде не роняет старт, а тихо включает подпись токенов пустым ключом.
- **Риск:** подделка JWT любым внешним клиентом → полный обход аутентификации.
- **Приоритет:** 🔴 Critical
- **Что нужно сделать:** fail-fast на старте: если `jwt.secret` пуст или короче 32 символов — бросать исключение и не поднимать сервер. Один источник чтения секрета.
- **Когда делать:** немедленно, до следующего продового деплоя.

---

## ~~TD-002~~ — ~~CORS: `anyHost()` + `allowCredentials = true`~~ ✅ ЗАКРЫТ

- **Решено в:** 2026-07-07 (см. ENGINEERING_CHANGELOG «TD-002: CORS whitelist»)
- **Что сделано:** `anyHost()` заменён на whitelist из конфига `cors.allowedHosts` (env `CORS_ALLOWED_HOSTS`, через запятую). Дефолт: `loyaltyloops.app`, `www.loyaltyloops.app`, `loyalityloop-beta.up.railway.app`, `localhost:3000`. Мобильные клиенты не затронуты — они не отправляют Origin, CORS ограничивает только браузерные запросы.

---

## ~~TD-003~~ — ~~Нет блокировок при денежных операциях~~ ✅ ЗАКРЫТ

- **Решено в:** 2026-07-07 (см. ENGINEERING_CHANGELOG «TD-003: сериализация денежных операций»)
- **Что сделано:** `processTransaction` первым шагом берёт `SELECT ... FOR UPDATE` на строку карты (`LoyaltyCardRepository.lockCardRow`) — параллельные операции по одной карте сериализуются. Страховка вторым слоем: списание в `addCashback` выполняется условным UPDATE (`WHERE balance >= сумма списания`), при 0 обновлённых строк — `LoyaltyException(INVALID_AMOUNT, "Insufficient balance")`. Конкурентный тест вынесен в TD-019.

---

## ~~TD-004~~ — ~~Схема БД через `createMissingTablesAndColumns` вместо миграций~~ ✅ ЗАКРЫТ

- **Решено в:** 2026-07-07 (см. ENGINEERING_CHANGELOG «TD-004: Flyway»)
- **Что сделано:** внедрён Flyway 9.22.3. `V1__baseline.sql` — полная текущая схема (23 таблицы, на существующих БД не выполняется благодаря `baselineOnMigrate=true`), `V2__loyalty_cards_balance_check.sql` — CHECK `balance >= 0 NOT VALID`. `DatabaseFactory.init`: Postgres → Flyway; H2 (тесты) → прежний `createMissingTablesAndColumns`. Новое правило: изменение схемы = table object + миграция `V{N}__description.sql`.

---

## TD-005 — WebSockets: `maxFrameSize = Long.MAX_VALUE`

- **Область:** server / WebSockets
- **Проблема:** кадр любого размера принимается в память.
- **Почему это техдолг:** значение по принципу «чтобы работало», лимит не выбран осознанно.
- **Риск:** OOM / DoS одним злонамеренным клиентом.
- **Приоритет:** 🟠 High
- **Что нужно сделать:** ограничить (для текущих сценариев достаточно 64 КБ), обработать закрытие соединения при превышении.
- **Когда делать:** вместе с TD-001/TD-002 (один security-патч).

---

## TD-006 — `/health` доступен только при `features.enableTestSupport=true`

- **Область:** server / observability
- **Проблема:** health-эндпоинт спрятан за флагом тестовой поддержки — на проде его нет.
- **Почему это техдолг:** мониторинг и оркестратор не видят состояние сервиса и БД.
- **Риск:** тихие деградации, downtime без алертов.
- **Приоритет:** 🟠 High
- **Что нужно сделать:** вынести `/health` из-под флага, добавить в деплой healthcheck.
- **Когда делать:** ближайший деплой.

---

## TD-007 — Сидинг супер-админа в `launch` без обработки ошибок

- **Область:** server / Application.kt
- **Проблема:** сидинг выполняется в `launch` на старте; исключение (кроме `ALREADY_JOINED`) пробрасывается в корутину без обработчика. Логи — через `println`.
- **Почему это техдолг:** временный бутстрап-код, оставшийся в проде.
- **Риск:** падение приложения после старта; несидированный админ без внятного лога.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** обернуть весь блок в try/catch с логгером; рассмотреть перенос в отдельный CLI/миграцию.
- **Когда делать:** попутно с задачами в Application.kt.

---

## TD-008 — `environment!!.config` в routing

- **Область:** server / Application.kt
- **Проблема:** 4 использования `!!` на nullable environment.
- **Почему это техдолг:** локальная переменная `envConfig` уже есть — `!!` просто дублирует доступ небезопасным способом.
- **Риск:** низкий (NPE маловероятен), но это шум и плохой пример.
- **Приоритет:** 🟢 Low
- **Что нужно сделать:** использовать `envConfig` везде.
- **Когда делать:** попутно.

---

## TD-009 — `isLenient = true` в серверной JSON-сериализации

- **Область:** server / ContentNegotiation
- **Проблема:** сервер принимает синтаксически некорректный JSON.
- **Почему это техдолг:** маскирует ошибки клиентов, усложняет диагностику.
- **Риск:** тихое принятие мусорных данных.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** убрать `isLenient`, прогнать интеграционные тесты, проверить клиентов.
- **Когда делать:** после стабилизации мобильных клиентов.

---

## TD-010 — `println` вместо логгера

- **Область:** server (DatabaseFactory.ping, сидинг), местами в сервисах
- **Проблема:** часть диагностики пишется в stdout мимо slf4j/logback.
- **Почему это техдолг:** нет уровней, таймстемпов, структуры; теряется при агрегации логов.
- **Риск:** слепые зоны при инцидентах.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** заменить на `logger.*`; detekt-правило `ForbiddenMethodCall` на `println` в server.
- **Когда делать:** одним проходом по server.

---

## TD-011 — web-admin: 99 использований `: any`

- **Область:** web-admin / TypeScript
- **Проблема:** типизация выключена в значительной части кода.
- **Почему это техдолг:** TypeScript-gate (0 ошибок tsc) не ловит реальные ошибки, если всё `any`.
- **Риск:** runtime-ошибки на проде, которые обязан ловить компилятор.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** включить `noImplicitAny` + `@typescript-eslint/no-explicit-any` (warn → error), выпиливать поэтапно, DTO-типы генерировать/выносить в `types/`.
- **Когда делать:** поэтапно, каждая фича уменьшает счётчик.

---

## TD-012 — Access-токен web-admin в `localStorage`

- **Область:** web-admin / auth
- **Проблема:** токен хранится в `localStorage`.
- **Почему это техдолг:** уязвим к XSS; выбран как простое решение.
- **Риск:** кража админского токена через любую XSS-инъекцию.
- **Приоритет:** 🟠 High
- **Что нужно сделать:** httpOnly secure cookie + refresh-flow, либо как минимум CSP и аудит XSS-поверхности.
- **Когда делать:** вместе с security-патчем TD-001/002.

---

## TD-013 — 94 TODO/FIXME без реестра

- **Область:** весь репозиторий
- **Проблема:** TODO разбросаны по коду, включая `// TODO Checked` над TransactionService.
- **Почему это техдолг:** намерения не отслеживаются, часть устарела.
- **Риск:** забытые известные проблемы.
- **Приоритет:** 🟢 Low
- **Что нужно сделать:** разобрать: актуальные → сюда как TD-записи, мусорные → удалить. Дальше — правило «TODO в коде запрещён, история живёт в TECH_DEBT.md».
- **Когда делать:** фоновая задача.

---

## TD-014 — Артефакты подписи в рабочей копии composeApp

- **Область:** composeApp / безопасность репозитория
- **Проблема:** в рабочей копии лежат неотслеживаемые `loyaltyloop_uploadcert.pem`, `pepk.jar`, `pepk_out.zip`.
- **Почему это техдолг:** ключевой материал рядом с кодом; один случайный `git add .` — и он в истории.
- **Риск:** утечка ключей подписи приложения.
- **Приоритет:** 🟠 High
- **Что нужно сделать:** добавить паттерны в `.gitignore`, перенести артефакты в защищённое хранилище вне репозитория.
- **Когда делать:** немедленно (в .gitignore уже добавлено — вынести файлы из репо).

---

## TD-015 — Нет DATABASE.md и DBML-схемы

- **Область:** docs
- **Проблема:** 23 таблицы Exposed не описаны ни текстом, ни DBML.
- **Почему это техдолг:** стандарт документации (см. CLAUDE.md) требует `DATABASE.md` + `.dbml`; сейчас единственный источник схемы — код.
- **Риск:** онбординг и ревью схемы только по коду; ошибки проектирования незаметны.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** сгенерировать `docs/DATABASE.md` + `docs/master.dbml` из текущих table objects; далее поддерживать по правилу «изменил схему — обнови оба».
- **Когда делать:** вместе с TD-004 (Flyway), чтобы зафиксировать baseline.

---

## ~~TD-018~~ — ~~Docker-сборка качает Gradle-дистрибутив на каждом деплое~~ ✅ ЗАКРЫТ

- **Решено в:** 2026-07-07 (см. ENGINEERING_CHANGELOG «TD-017/TD-018: детерминированная Docker-сборка»)
- **Что сделано:** образ поднят до `gradle:8.7.0-jdk17` (совпадает с wrapper), сборка через предустановленный `gradle :server:installDist` — скачивание дистрибутива из сборки исключено. Подстраховка: `networkTimeout=120000` в `gradle-wrapper.properties`.

---

## ~~TD-017~~ — ~~`-Xmx16g` в gradle.properties применяется и в Docker-сборке~~ ✅ ЗАКРЫТ

- **Решено в:** 2026-07-07 (см. ENGINEERING_CHANGELOG «TD-017/TD-018: детерминированная Docker-сборка»)
- **Что сделано:** в Dockerfile добавлен `ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=512m ..."` — переопределяет локальные 16g внутри контейнера сборки.

---

## TD-020 — Легаси-долг detekt (~400 замечаний, зафиксирован в baseline)

- **Область:** server (в основном), composeApp, shared
- **Проблема:** `:server:detekt` находил ~400 замечаний и валил сборку. Долг заморожен в `config/detekt/baseline-*.xml` — новый код проверяется строго, старый не блокирует build.
- **Почему это техдолг:** нарушения реальные, просто отложенные. Приоритетные категории: `TooGenericExceptionCaught`/`SwallowedException` (~50 — проглоченные ошибки скрывают инциденты), подозрительные `UnusedParameter`/`UnusedPrivateProperty` (например, `SupportChatRepository.viewerIsPartner`, неиспользуемые константы анти-абьюза в `RatingService` — возможно, потерянная логика), сложность (`authRoutes` CC=38, `partnerRoutes` 366 строк), `WildcardImport` (~30 файлов), `MagicNumber` (шум, много в table objects).
- **Риск:** baseline «замораживает» и настоящие баги (проглоченные исключения, мёртвые параметры).
- **Приоритет:** 🟡 Medium (пункт про Swallowed/Unused — 🟠 High)
- **Что нужно сделать:** выжигать по категориям; при каждом рефакторинге файла — чистить его из baseline; не добавлять новые записи в baseline без ревью.
- **Когда делать:** инкрементально.
- **Прогресс 2026-07-07:** ✅ SwallowedException (14/14): осознанные фоллбэки помечены `catch (_: ...)` (конвенция detekt), потеря исключений исправлена — `LoyaltyException` теперь принимает `cause`, в `MapsRoutes` добавлен `logger.warn` вместо тихого `emptyList`. ✅ Unused в main-коде (7): удалены мёртвые константы анти-абьюза в `RatingService`, неиспользуемые параметры `viewerIsPartner`, `checkForRolePermission(userId)`, `checkExistingRole(role)`, `mapsRoutes(partnerRepository)`, логгер в `AuthSessionRepository`. Удалён дубль `server/detekt-baseline.xml`. Остались: Unused в тестах (полудописанные проверки в `ValidationUtilsTest`/`AuthTest` — требуют дописывания, не удаления), сложность роутов, WildcardImport. Конфиг detekt перенастроен на значимое: MagicNumber игнорирует объявления свойств (varchar-длины и т.п.), MaxLineLength не проверяет `i18n/`, для exceptions-правил явно закреплена конвенция `ignored|expected|_`. После изменения конфига baseline перегенерировать.
- **Прогресс 2026-07-09 (pass 2, baseline 439→206→~183):** ✅ WildcardImport 80/80 (Optimize Imports). ✅ VariableNaming 13/13: camelCase для приватных полей (`cacheTtlSeconds`, алиасы таблиц, локализационные map в TelegramAuthService). ✅ MatchingDeclarationName 3/3: `LoyaltyCardTable.kt`→`LoyaltyCardsTable.kt`, `AuthSessionsDto.kt`→`AuthSessionStatus.kt`, `WaitlistRequest` вынесен в `models/`. ✅ TooGenericExceptionThrown 2/2 + UseCheckOrError 2/2: `error(...)` вместо `throw Exception/IllegalStateException`. ✅ InstanceOfCheckForException: отдельный catch для `CancellationException` (+ cause в LoyaltyException). Остаётся крупное: сложность роутов (`authRoutes`, `partnerRoutes`), ReturnCount/ThrowsCount.
- **Прогресс 2026-07-09 (pass 3):** ✅ Реанимированы тесты: `AuthTest` — 3 теста раскомментированы и адаптированы (OTP-флоу, ротация refresh-токена с ревокацией, отклонение истёкшего токена — ожил `generateExpiredToken`); `PartnerConstraintsTest` — дописаны `owner cannot create duplicate partner` (409 BUSINESS_ALREADY_EXISTS) и `pin reset freezes account` (заморозка блокирует мутирующие запросы). ✅ Удалена мёртвая инъекция `emailTemplatesService` в Application.kt. Отложено (сценарии в git-истории файлов): `tokenOfDeletedUser_isRejected` (нужен хелпер registerAsAdmin), e-mail-флоу сброса PIN (нужен EmailDebugStore).
- **Прогресс 2026-07-09 (pass 4, baseline 67 → ~30):** ✅ `TimezoneUtils` — when-цепочки → таблицы (попутно баг: валюта для Volgograd/Kirov/Barnaul была рассинхронизирована со страной). ✅ Политика в конфиге: guard-клаузы исключены из ReturnCount/ThrowsCount, DI-конструкторы ≤10 параметров. Остаток — только структурный рефакторинг: сложность/размер `authRoutes` (CC 38) и `partnerRoutes` (CC 56, 366 строк), LongParameterList route-функций (лечится Koin-инъекцией внутри роутов), generic catch на границах (легитимны, каждый логирует), `TieredLoyaltyTest` — реанимировать E2E денежного флоу.
- **Прогресс 2026-07-09 (pass 3, baseline 179→162):** ✅ TrailingWhitespace 16/16: пробелы в хвостах строк убраны в 16 файлах server (routes/service/repository); трогали только обычные строки, raw-строки идут через `.trimIndent()`. ✅ UnusedPrivateProperty 1: `SupportChatWebSocketHandler.drainIncoming` — `for (frame in incoming)` заменён на `session.incoming.consumeEach { }` (unused-переменная устранена). Оставлены в baseline осознанно: `AuthTest.generateExpiredToken` и `PartnerConstraintsTest.owner` — полудописанные тесты (дописать, не удалять). ⚠️ baseline правился вручную (в среде JDK 11, `:server:detekt` не прогонялся) — при следующем прогоне detekt перегенерировать `baseline-server.xml` для сверки.
- **Прогресс 2026-07-09 (pass 5, baseline 143→118):** ✅ MagicNumber batch 1 — 25/63 в 6 файлах (тайминги/пулы/TTL/окна аналитики). Литералы вынесены в именованные `const val` (companion для классов, top-level для `Application`/`ServiceModule`, object-level для `DatabaseFactory`) — detekt игнорирует числа в объявлениях свойств, поведение не меняется. Файлы закрыты полностью: `DatabaseFactory` (пул Hikari), `RedisService` (дефолты пула Jedis), `ExchangeRateService` (интервал апдейта, длина кода валюты), `ServiceModule` (SMS-лимиты), `Application` (WS ping/timeout, refill rate-limit, HTTP-таймаут, TG-сессия), `AnalyticsService` (окна week/month/6m/year через `DAY_MS`). Типы Int/Long сверены (суффикс `L` где свойство Long). Осталось 38 MagicNumber в 16 файлах (ConfigRoutes 7, Rating/Platform/Maps по 4 и т.д.) — следующие batch'и. Проверка тут только чтением (detekt/тесты на JDK 17 не прогнать).
- **Прогресс 2026-07-09 (pass 6, baseline 120→111):** ✅ MagicNumber batch 2 — 9 записей в 4 файлах доменного кластера «рейтинг/trust-score»: `RatingService` (trust-дефолт/макс, пороги risk-level GREEN/YELLOW, окно последних оценок), `RowMappers` (те же пороги risk-level), `RatingRepository` (NPS promoter/detractor), `LoyaltyCardRepository` (стартовый trustScore). Литералы вынесены в `const val` (companion/top-level). Кандидат на follow-up: пороги `RISK_GREEN_THRESHOLD=4.5`/`RISK_YELLOW_THRESHOLD=3.5` продублированы в `RatingService` и `RowMappers` — стоит вынести единый `RiskLevel.fromTrustScore(...)` в `shared` (отдельный рефакторинг + тесты). Осталось 29 MagicNumber в 12 файлах (ConfigRoutes 7, MapsRoutes/PlatformRepository по 4…).
- **Прогресс 2026-07-09 (pass 7, batch 3 — гео/карты):** ✅ MagicNumber в `ConfigRoutes` (радиусы карты min/default/max/cluster, debounce, дефолтные координаты KG), `MapsRoutes` (те же радиусы, дефолтный лимит поиска, макс. рейтинг), `MapRepository` (bbox-padding 1.1, минут в часе 60) → `const val`. Побочно: вынесение `RISK_GREEN_THRESHOLD` в `RatingService` удлинило baselined-строку `if` (ComplexCondition) — условие вынесено в именованный boolean `isAntiAbuseIgnored`, что убрало и ComplexCondition, и MaxLineLength (обе записи вычищены). `MapRepository` длинная строка bbox перенесена. Дубль дефолтов радиусов карты в `ConfigRoutes`+`MapsRoutes` — кандидат на общий источник (map-defaults). ⚠️ Замечено: рабочее дерево на старте уже содержало незакоммиченные изменения baseline/кода (сигнатуры в baseline местами уже отражали переименования) — абсолютные счётчики «плыли»; правки делались по фактическому содержимому файлов, финальная сверка — `./gradlew :server:detekt` на JDK17.
- **Прогресс 2026-07-09 (pass 8, batch 4 — MagicNumber почти закрыт, baseline →75):** ✅ Вынесены в `const val` (top-level): `LoyaltyEngineService` (warmup-задержка, окно предупреждения об истечении), `OtpService` (верхняя граница кода), `PartnerRepository` (дефолт визитов, порог неактивности 6 мес, длина PIN-суффикса телефона), `PartnerRoutes` (дефолтный лимит страницы), `PlatformRepository` (триал 14 дней), `SubscriptionRepository` (окно предупреждения), `SystemStaffRepository` (срок инвайта 24ч), `TelegramAuthService` (интервал health-check), `TransactionService` (кол-во частей QR). **Оставлены осознанно (3 записи):** `PlatformRepository` `plusMonths(3)/plusMonths(6)/plusYears(5)` в `when(SubscriptionDuration)` — число дублирует имя enum-константы (MONTH_3→3), отдельная константа только зашумит. Чистый фикс — дать `SubscriptionDuration` свойство длительности или `advance(start)` в `shared` (отдельный рефакторинг + тесты). Итог MagicNumber: 63→3.
- **Прогресс 2026-07-09 (pass 9, консолидация дублей констант):** при выносе MagicNumber появились одинаковые константы в разных файлах — сведены к единым источникам: (1) пороги risk-level → `RiskLevel.fromScore(score, fraud)` + `GREEN/YELLOW/ORANGE_MIN_SCORE` в `shared` (убрало дубль порогов **и** дублирующуюся `when`-лестницу в `RatingService`+`RowMappers`); (2) дефолты радиусов карты → `object MapDefaults` (routes) для `ConfigRoutes`+`MapsRoutes`; (3) окно предупреждения об истечении → `object SubscriptionPolicy` (utils, слой ниже service/repository) для `SubscriptionRepository`+`LoyaltyEngineService`. Поведение идентично, baseline не изменился. Оставлены раздельными осознанно (совпадает значение, но разный смысл): `TRUST_SCORE_MAX`(5.0)/`MAX_RATING`(5.0), `DEFAULT_SEARCH_LIMIT`(50)/`DEFAULT_PAGE_LIMIT`(50).
- **Прогресс 2026-07-09 (pass 10, безопасные структурные, baseline 75→72):** ✅ ComplexCondition `GeoIpService` — IP-проверка вынесена в `isLocalOrPrivateIp()`. ✅ InstanceOfCheckForException `ExchangeRateService` — `if (e is CancellationException) throw e` заменён на отдельный `catch (CancellationException)`. ✅ LongParameterList `HistoryRepository.recordTransaction` (11 параметров) → `data class TransactionRecord` (3 call-site'а в `TransactionService`, все именованные). Эмпирически подтверждено: detekt по дефолту игнорирует конструкторы data-классов (`SystemEventFilter` с 10 полями не во flagged), поэтому параметр-объект реально убирает LPL, а не переносит. Остаток LongParameterList (9): метод-уровневые (`LoyaltyCalculator.calculate`, 3 приватных метода `TransactionService`) — тот же безопасный паттерн `data class`, но много call-site'ов; роуты/конструкторы (`AdminRoutes`, `AuthRoutes`, `PartnerRoutes`, конструкторы `TransactionService`/`TelegramAuthService`) — затрагивают Koin DI и `Application.kt`, делать на JDK17 с прогоном.
- **Отложено — TooGenericExceptionCaught (12 записей, ~30 физических catch):** почти все catch логируют исключение (`logger.error(..., e)` / `e.message`) на границах I/O (SMS, email, HTTP, geoip, курсы, фоновые циклы) — это легитимный «поймать и залогировать», а не «проглот» (настоящие проглоты закрыты в pass 1). Убрать замечание можно только сужением типа catch, что меняет поведение (непойманные типы начнут пробрасываться). Требует прогона `:server:test` + detekt для верификации — делать в среде с JDK 17, не вслепую.
- **Прогресс 2026-07-09 (pass 4, baseline 162→143):** ✅ MaxLineLength 19/23: перенос длинных строк в 12 файлах server. Приёмы без изменения поведения — разбивка сигнатур/вызовов по аргументам (`deleteTokenExact`, `removeStaffMember`, `eventLogger.log`), вынос `?:`/`throw` на отдельную строку (`AdminPlatformRoutes`, `AuthRoutes`, `PlatformRepository`, `SystemStaffRepository` ×2), многострочные условия `when` (`ErrorHandler`), конкатенация лог-строки (`Application`), разбиение HTML на несколько `sb.append(...)` (`EmailTemplate` ×7, вывод идентичен). Оставлены 4 записи `TieredLoyaltyTest` — закомментированный код полудописанных тестов. CSS в raw-string HTML-шаблоне (`EmailTemplate`, ~4 строки >140) detekt не флагует и в baseline нет — не трогали. Находка: `AppErrorCode.RATE_LIMIT_EXCEEDEG` — опечатка в имени enum (EXCEEDED), но значение сериализуется в API-ответы → переименование ломающее, отложено.

---

## TD-019 — Нет конкурентного теста на списание баллов

- **Область:** server / testing
- **Проблема:** защита от гонки при списании (TD-003) не покрыта автоматическим конкурентным тестом.
- **Почему это техдолг:** регрессию (например, случайное удаление `lockCardRow`) сейчас поймает только прод.
- **Риск:** возврат double-spending незамеченным.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** тест на H2 (MODE=PostgreSQL): карта с балансом X, два параллельных `POST /terminal/process` со списанием X каждый; ассерты — ровно одна успешна, вторая получает `INVALID_AMOUNT`, итоговый баланс ≥ 0.
- **Когда делать:** следующее касание TransactionService.

---

## TD-016 — Экранная документация отсутствует

- **Область:** docs / composeApp, web-admin
- **Проблема:** ни один экран не описан парой SCREEN + SCREEN_SPEC.
- **Почему это техдолг:** стандарт принят (SCREEN_DOCUMENTATION_GUIDE.md), но покрытие нулевое.
- **Риск:** спеки живут в головах; ревью «соответствие спеку» невозможно.
- **Приоритет:** 🟡 Medium
- **Что нужно сделать:** документировать экраны по мере касания: тронул экран в фиче — создал/обновил пару документов. Начать с критического пути (сканирование QR, транзакция).
- **Когда делать:** инкрементально, начиная со следующей фичи.
