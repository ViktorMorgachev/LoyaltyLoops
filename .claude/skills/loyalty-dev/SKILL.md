---
name: loyalty-dev
description: "Development module for the LoyaltyLoops project. Use this skill for any new feature, bugfix, or refactoring task in the LoyaltyLoops codebase. Triggers: 'реализуй', 'разработай', 'добавь фичу', 'implement', 'build feature', 'fix this', 'refactor'. Always use this skill when writing code for LoyaltyLoops — it knows the exact project structure, conventions, and workflow. Do NOT start coding without this skill active."
---

# LoyaltyLoops Development Module

Ты реализуешь фичи для проекта **LoyaltyLoops** — кроссплатформенная система лояльности (Kotlin Multiplatform монорепо).

---

## Структура проекта

```
LoyaltyLoop/
├── server/           # Kotlin + Ktor + Koin + Exposed + PostgreSQL
│   └── src/main/
│       ├── kotlin/io/loyaltyloop/server/
│       │   ├── routes/          # Route handlers (тонкие)
│       │   ├── service/         # Бизнес-логика
│       │   ├── repository/      # Доступ к данным (Exposed)
│       │   ├── database/        # DatabaseFactory, tables/
│       │   ├── di/              # Koin-модули
│       │   ├── websocket/       # WS-хендлеры
│       │   └── utils/           # ErrorHandler, LoyaltyException
│       └── resources/openapi/documentation.yaml   ← OpenAPI spec
├── shared/           # KMP: DTO, AppErrorCode, валидация, утилиты
├── composeApp/       # Compose Multiplatform (Android/iOS)
│   └── src/commonMain/kotlin/io/loyaltyloop/app/
│       ├── features/        # Экраны по фичам (auth, main, map, tabs…)
│       ├── ui/theme/        # Color.kt, Theme.kt — все цвета здесь
│       ├── navigation/      # Voyager
│       ├── di/              # Koin
│       └── data|repository|services/
├── web-admin/        # React 19 + TypeScript + Vite + MUI v6
│   └── src/
│       ├── pages/           # Страницы (+ admin/, partner/, platform/)
│       ├── api/             # axiosConfig + API-клиенты
│       ├── context/         # UserContext и др.
│       ├── i18n/            # Все строки UI
│       └── theme.ts         # Тема MUI — все цвета здесь
└── docs/             # Документация (карта — в CLAUDE.md)
```

---

## Tech Stack

| Слой | Стек |
|---|---|
| Backend | Kotlin, Ktor (Netty), Koin, Exposed, PostgreSQL, HikariCP, Redis, WebSockets, JWT+OTP |
| Mobile | Compose Multiplatform, Voyager, Koin, Ktor Client, SQLDelight, Yandex Maps |
| Web Admin | React 19, TypeScript, Vite, MUI v6, Axios, i18next |
| Infra | Docker (multi-stage), detekt, Gradle 8.7 / JDK 17 |

---

## Ключевые соглашения

### Backend (Kotlin)
- Слои: `routes → services → repositories`. Route handlers тонкие, бизнес-логика в сервисах
- Ошибки: только `throw LoyaltyException(AppErrorCode.*)` — HTTP-маппинг централизован в `utils/ErrorHandler.kt`. Новый код ошибки → добавить в `AppErrorCode` (shared) и в маппинг
- DTO живут в `shared` (общие с мобильными клиентами)
- БД-доступ: через `DatabaseFactory.dbQuery { }`; денежные операции — в одной транзакции с блокировкой строки (см. TD-003)
- Схема БД: Exposed table objects + `createMissingTablesAndColumns` (Flyway — TD-004). Деструктивные изменения — только осознанно, с бэкапом
- Новый endpoint без записи в `documentation.yaml` = задача не завершена
- Логи — только slf4j-логгер, `println` запрещён
- Время — везде UTC (`docs/ENGINEERING_NOTES.md`)

### composeApp (Compose Multiplatform)
- Цвета — только из `ui/theme/Color.kt` / `Theme.kt`, никаких хардкодных `Color(0xFF...)` в экранах
- Строки — через систему локалей (`scripts/generateComposeLocales.cjs`), не хардкодом
- Навигация — Voyager; DI — Koin
- Каждый data-экран обрабатывает состояния: loading / error (+retry) / empty / ready

### web-admin (React + TypeScript)
- Цвета и стили — через тему MUI (`src/theme.ts`); хардкодные hex в `sx`/CSS запрещены
- Все строки UI — через i18n (`src/i18n/`), не хардкодом в JSX
- API-вызовы — через клиенты в `src/api/` (единый `axiosConfig`)
- Никаких новых `: any` — типы в `src/types/` (реестр текущих нарушений: TD-011)
- Каждый data-экран: loading / error (+retry) / empty / ready

---

## Рабочий процесс

### Шаг 1: Читаем контекст

- **Технический долг в зоне фичи → `docs/TECH_DEBT.md`** (обязательно!). Открытый TD в зоне — либо чинить попутно (отдельным коммитом), либо явно зафиксировать «не трогаем, потому что…». Никогда не делать вид, что TD не существует
- Контекст фичи → `docs/tasks/STEP_*.md` если есть
- Изменения экрана → `docs/screens/**/*_SCREEN_SPEC.md` если есть (покрытие пока нулевое — TD-016: тронул экран — создай пару документов по `docs/SCREEN_DOCUMENTATION_GUIDE.md`)
- Изменения API → `server/src/main/resources/openapi/documentation.yaml`
- Технические флоу → `docs/flows/*` (транзакции, QR, пуши, авторизация)

### Шаг 2: Разбиваем на атомарные задачи

TodoList с независимо проверяемыми подзадачами. Не больше одной подзадачи одновременно.

### Шаг 3: Реализуем итеративно

1. Реализуем изменение
2. web-admin: `cd web-admin && npx tsc --noEmit` — ноль ошибок
3. Kotlin: `./gradlew :server:build` — без ошибок
4. Готовим текст коммита `type(scope): summary` именно для этой подзадачи

### Шаг 4: Gates перед завершением

- **web-admin: ноль ошибок tsc — обязательное условие**
- **server: компиляция + существующие тесты (`./gradlew :server:test`) зелёные**

### Шаг 5: После реализации

- Новый endpoint → `documentation.yaml`
- Тронут экран → пара `*_SCREEN.md` + `*_SCREEN_SPEC.md`
- Изменена схема БД → обновить table object (+ `docs/DATABASE.md`/`.dbml` когда появятся — TD-015)
- Шип → запись в `docs/ENGINEERING_CHANGELOG.md`

---

## Паттерны

### Добавление нового backend endpoint

1. DTO в `shared` (если нужен новый)
2. Repository-метод в `repository/`
3. Бизнес-логика в `service/`
4. Route в `routes/` (тонкий, с проверкой access через `AccessControlService`)
5. Подключить в Koin-модуль (`di/`) и в `Application.kt` routing
6. Добавить в `documentation.yaml`
7. Тест в `server/src/test/`

### Добавление колонки в БД

1. Обновить table object в `database/tables/`
2. Обновить DTO в `shared` + RowMapper
3. Обновить repository
4. Проверить, что `createMissingTablesAndColumns` добавит колонку (nullable или с default!)
5. Обновить `documentation.yaml`, если колонка видна в API

### Добавление экрана web-admin

1. `pages/ScreenNamePage.tsx` (или в `pages/{admin|partner|platform}/`)
2. Строки → `i18n/`, цвета → `theme.ts`
3. API-клиент в `src/api/`
4. Route в роутинг приложения
5. Пара `docs/screens/webadmin/SCREEN_NAME.md` + `_SPEC.md`

---

## Что НЕ делать

- Не хардкодить цвета (MUI theme / Color.kt) и строки (i18n / локали)
- Не помещать бизнес-логику в route handlers
- Не бросать «сырые» исключения — только `LoyaltyException(AppErrorCode.*)`
- Не добавлять endpoint без `documentation.yaml`
- Не писать `println` в server-коде
- Не добавлять новые `: any` в web-admin
- Не делать read-modify-write баланса без блокировки строки
- Не документировать несуществующую реализацию как готовую

### Лаконичность комментариев и описаний

**Минимум прозы — везде.** Если без комментария код понятен — комментария быть не должно.

- **Никаких TD-XXX / STEP_X / истории изменений** в коде, OpenAPI, SCREEN_SPEC. История живёт в git, `ENGINEERING_CHANGELOG.md`, `TECH_DEBT.md`
- **Не дублировать** то, что видно из имени, типа, условия, схемы или соседнего файла
- **OpenAPI `description`** — одно предложение по сути
- **Комментарий нужен только если** без него код неверно интерпретируется (ordering, инвариант, неочевидный edge case)

Перед каждым новым комментарием спроси себя: «Поймёт ли разработчик код без этой строки?» Если да — удали.
