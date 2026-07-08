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
