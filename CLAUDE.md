# LoyaltyLoops — Claude Orchestration Guide

Этот файл управляет оркестрацией Claude в проекте LoyaltyLoops.
Перед началом любой задачи — найди нужный скилл в таблице ниже и прочитай его `SKILL.md`.

Скиллы находятся по пути: `.claude/skills/<skill-name>/SKILL.md`

---

## Skill Map

| Тип задачи | Скилл |
|---|---|
| Реализовать фичу, починить баг, рефакторинг | `loyalty-dev` |
| Код-ревью перед мержем | `loyalty-review` |
| Обновить документацию после изменений в коде | `loyalty-docs` |
| Дизайн-ревью, проверка темы/токенов, UX-копирайт | `loyalty-design` |

---

## Обзор проекта

**LoyaltyLoops** — кроссплатформенная система управления лояльностью (Kotlin Multiplatform монорепо).

| Директория | Содержимое |
|---|---|
| `composeApp/` | Compose Multiplatform (Android/iOS): единое приложение для Клиентов, Кассиров, Владельцев |
| `server/` | Kotlin + Ktor + Koin + Exposed + PostgreSQL + WebSockets |
| `shared/` | KMP: DTO, константы, валидация, утилиты (сервер + мобильные клиенты) |
| `web-admin/` | React 19 + TypeScript + Vite + MUI v6 (панель управления) |
| `docs/` | Вся документация: флоу, операции, тесты, задачи, техдолг |

---

## Стандартный рабочий цикл (Vibe Coding Loop)

```
1. Читаем контекст → docs/tasks/* + docs/TECH_DEBT.md (обязательно!) + docs/screens/**/*_SPEC.md если есть
2. [loyalty-dev]    Реализуем небольшими атомарными шагами, TypeScript/компиляция gate
3. [loyalty-review] Двухфазное ревью: сначала Claude, потом Viktor
4. [loyalty-docs]   Синхронизируем доки: CHANGELOG, openapi, TECH_DEBT, SPEC-статусы
5. [loyalty-design] Дизайн-чек: тема MUI / Compose theme, копирайт
6. Коммитим и шипим
```

---

## Ключевые соглашения (быстрая справка)

### Код

- **server**: слои `routes → services → repositories`; бизнес-логика в сервисах, route handlers тонкие
- **Ошибки server**: только `LoyaltyException(AppErrorCode.*)` — маппинг в HTTP в `ErrorHandler.kt`
- **БД**: schema через Exposed table objects + `createMissingTablesAndColumns` (переход на Flyway — TD-004). Деструктивные изменения схемы — только осознанно, с бэкапом
- **DTO** — в `shared` (используются сервером и мобильными клиентами)
- **API changes**: всегда обновляем `server/src/main/resources/openapi/documentation.yaml`
- **web-admin**: цвета/стили через тему MUI (`src/theme.ts`), не хардкод в sx; строки через i18n (`src/i18n/`), не хардкод в JSX
- **web-admin**: `npx tsc --noEmit` — обязательный gate, ноль ошибок
- **composeApp**: цвета только из `ui/theme/Color.kt` / `Theme.kt`; строки через локали (см. `scripts/generateComposeLocales.cjs`)
- **Время**: везде UTC (см. `docs/ENGINEERING_NOTES.md`)
- **Логи server**: только slf4j-логгер, `println` запрещён

### Комментарии и описания — только если без них непонятно

Минимум прозы в коде, OpenAPI и SPEC:

- **Никаких TD-XXX/STEP-X в комментариях.** История живёт в git, `ENGINEERING_CHANGELOG.md` и `TECH_DEBT.md`.
- **Не дублируем то, что уже видно** из имени, типа, условия, схемы или соседнего файла.
- **OpenAPI `description`** — одно предложение по сути. Без внутренних правил и истории изменений.
- **Комментарий пишем тогда и только тогда**, когда без него код неверно интерпретируется.
- **После каждой завершённой задачи** — готовый текст коммита в формате `type(scope): summary`.

---

## Структура документации

### Экранная документация

Каждый экран описывается **парой документов** `*_SCREEN.md` + `*_SCREEN_SPEC.md`.
Модули: `docs/screens/{client|cashier|partner|webadmin|shared}/`.
Единственный гайд: `docs/SCREEN_DOCUMENTATION_GUIDE.md` (правила пар, обязательные блоки SPEC, ASCII-стандарт «Визуальной карты экрана», шаблон).
Покрытие сейчас нулевое (TD-016): документируем экран при каждом его изменении.

### Карта документов

| Файл | Назначение |
|---|---|
| `server/src/main/resources/openapi/documentation.yaml` | OpenAPI spec (обновлять с каждым endpoint'ом) |
| `docs/TECH_DEBT.md` | Реестр техдолга (TD-N). **Читать перед фичей**, обновлять при закрытии/появлении долга |
| `docs/ENGINEERING_CHANGELOG.md` | Журнал изменений (запись при каждом шипе) |
| `docs/ENGINEERING_NOTES.md` | Архитектурные решения |
| `docs/TECH_STACK.md` | Используемые технологии |
| `docs/PARTNER_GUIDE.md` | Бизнес-функционал для партнёров |
| `docs/flows/` | Технические флоу: транзакции, QR, пуши, авторизация, мультивалютность, рейтинги |
| `docs/operations/` | Деплой, настройка БД/бота, переводы |
| `docs/testing/` | Тест-кейсы, нагрузочное тестирование |
| `docs/tasks/` | Планы реализации **активных** этапов |
| `docs/archive/tasks/` | Сданные планы — не редактировать |
| `docs/reviews/` | Документы код-ревью |

---

## Протокол ревью

Viktor участвует в каждом ревью. Процесс:

1. Claude делает автоматизированный анализ (Phase 1) — diff, спек, компиляция/tsc, безопасность
2. Claude представляет результаты с разделом «Learning Moments»
3. Viktor добавляет свои наблюдения
4. Claude объединяет оба взгляда в `docs/reviews/review-{date}-{feature}.md`

Цель: не просто найти баги — каждое ревью должно быть возможностью научиться чему-то новому.
