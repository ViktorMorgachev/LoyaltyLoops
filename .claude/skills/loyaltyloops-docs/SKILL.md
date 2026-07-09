---
name: loyaltyloops-docs
description: "Documentation sync module for LoyaltyLoops. Use after any code change to keep docs in sync with implementation. Triggers: 'актуализируй документацию', 'обнови доку', 'sync docs', 'update docs', 'документация устарела', 'что нужно обновить в доках'. Always use this skill after implementing a feature or fixing a bug — it knows which LoyaltyLoops docs to update and how. Do NOT let docs drift from code."
---

# LoyaltyLoops Documentation Sync Module

Поддерживает все документы проекта в актуальном состоянии.
Документарный дрейф — проектный долг: этот модуль его выявляет и устраняет системно.

---

## Карта: код → документация

| Что изменилось в коде | Какие документы обновить |
|---|---|
| Новый / изменённый API endpoint | `server/src/main/resources/openapi/documentation.yaml` |
| Новый экран (composeApp / web-admin) | пара `docs/screens/{module}/*_SCREEN.md` + `*_SCREEN_SPEC.md` |
| Изменилось поведение экрана | `*_SCREEN.md` + `*_SCREEN_SPEC.md` (states, transitions, edge cases) |
| Изменилась схема БД | table object — истина; `docs/DATABASE.md` + `.dbml` когда появятся (TD-015) |
| Изменился технический флоу (QR, пуши, авторизация, валюты) | соответствующий `docs/flows/*.md` |
| Изменился процесс деплоя / инфраструктура | `docs/operations/DEPLOYMENT.md`, `Dockerfile`-комментарии |
| Новая зависимость | `docs/TECH_STACK.md` |
| Архитектурное решение | `docs/ENGINEERING_NOTES.md` |
| Запущена новая фича | `docs/ENGINEERING_CHANGELOG.md` |
| Закрыт / выявлен технический долг | `docs/TECH_DEBT.md` (закрытый: `~~TD-N~~ ✅ ЗАКРЫТ` + дата + ссылка на CHANGELOG; новый: запись TD-N со всеми обязательными полями) |
| Изменились тест-кейсы | `docs/testing/TEST_CASES.md` |

---

## Workflow

### Шаг 1: Определить что изменилось

```bash
git diff --name-only HEAD~1 HEAD
# или для ветки:
git diff --name-only main...HEAD
```

### Шаг 2: Сопоставить изменения с документами

По каждому изменённому файлу определи затронутые документы (таблица выше).

### Шаг 3: Прочитать текущее состояние документов

Перед редактированием — прочитать документ, чтобы не затереть актуальную информацию.

### Шаг 4: Обновить документы

#### `documentation.yaml` (OpenAPI)

- Добавить новые paths/schemas, соответствующие реальным DTO из `shared`
- Обновить описания, если поведение изменилось
- `description` — одно предложение по сути, без истории изменений

#### Экранная документация (SCREEN + SCREEN_SPEC)

Единый источник правил: `docs/SCREEN_DOCUMENTATION_GUIDE.md`.

**`*_SCREEN.md`** (продуктовый): зачем экран, блоки, CTA. Не место для runtime-деталей.

**`*_SCREEN_SPEC.md`** (технический): статус реализации, States, Transitions, AC.
Каждое значимое состояние (`loading`, `default`, `error`, `empty`) — с ASCII-визуализацией.

#### `ENGINEERING_CHANGELOG.md`

Запись в начало:

```markdown
## {Дата} — {Название фичи}

### Внедрено
- Что реализовано
- Ключевые технические решения
- Краткий список изменённых файлов
```

### Шаг 5: Проверить, ничего не пропустили

```bash
git diff --name-only HEAD~1 HEAD | grep -v "^docs/"
```

По каждому не-docs файлу: либо есть обновление документации, либо явная фиксация «обновление не требуется» с причиной.

---

## Структура документации LoyaltyLoops

```
docs/
├── flows/          ← Технические флоу (LOYALTY_FLOW, PUSH_REALTIME, TELEGRAM_AUTH…)
├── operations/     ← DEPLOYMENT, DATABASE_MIGRATIONS, TELEGRAM_BOT_SETUP, TRANSLATIONS
├── testing/        ← TEST_CASES, LOAD_TESTING
├── screens/        ← Экранная документация: {client|cashier|partner|webadmin|shared}/
├── tasks/          ← Планы активных этапов
├── archive/tasks/  ← Сданные планы (не редактировать)
├── reviews/        ← Документы код-ревью
├── PARTNER_GUIDE.md
├── TECH_DEBT.md
├── TECH_STACK.md
├── ENGINEERING_CHANGELOG.md
├── ENGINEERING_NOTES.md
└── SCREEN_DOCUMENTATION_GUIDE.md

server/src/main/resources/openapi/
└── documentation.yaml  ← OpenAPI spec (не в docs/, а в server!)
```

---

## Формат отчёта

```
## Docs Sync Report

### Обновлено
- [x] server/.../documentation.yaml — добавлен POST /api/v1/... endpoint
- [x] docs/screens/webadmin/X_SCREEN_SPEC.md — статус: implemented
- [x] docs/ENGINEERING_CHANGELOG.md — запись для {feature}
- [x] docs/TECH_DEBT.md — TD-N помечен закрытым

### Обновление не требуется
- server/src/.../XDto.kt — внутренний DTO, не пользовательский
```

---

## Лаконичность комментариев и описаний

Полные правила — в `loyaltyloops-dev/SKILL.md` (раздел «Лаконичность»). Кратко:

- никаких TD-XXX / STEP_X в коде, OpenAPI, SCREEN_SPEC — история живёт в git и `ENGINEERING_CHANGELOG.md`;
- OpenAPI `description` — одно предложение по сути;
- комментарий нужен только если без него код неверно интерпретируется;
- если можно понять код без комментария — комментария быть не должно.
