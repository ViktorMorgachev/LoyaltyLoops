---
name: loyaltyloops-design
description: "Design sync module for LoyaltyLoops. Use for design-related tasks: checking consistency between code and theme/tokens, reviewing UX copy, updating screen specs. Triggers: 'проверь дизайн', 'обнови дизайн', 'посмотри на UI', 'design review', 'check tokens', 'review copy', 'ux copy', 'дизайн-система'. Always use this skill when making or reviewing visual/UX changes in LoyaltyLoops."
---

# LoyaltyLoops Design Sync Module

Поддерживает UI LoyaltyLoops в соответствии с дизайн-системой и спеками.
Охватывает обе UI-платформы: **composeApp** (Compose Multiplatform) и **web-admin** (MUI).

---

## Дизайн-система LoyaltyLoops

### Источники истины

| Платформа | Где живут цвета/стили | Где живут строки |
|---|---|---|
| composeApp | `ui/theme/Color.kt`, `Theme.kt`, `TierColors.kt` | локали (генерация: `scripts/generateComposeLocales.cjs`) |
| web-admin | `src/theme.ts` (тема MUI v6) | `src/i18n/` (i18next) |

### Правила

- **composeApp**: никаких `Color(0xFF...)` в экранах — только объекты из `ui/theme/`. Строки — только через локали
- **web-admin**: никаких хардкодных hex в `sx`/CSS — только palette темы. Строки — только через `t('key')`
- `docs/TECH_DEBT.md` просматривать перед дизайн-ревью: «дизайнерская проблема» может уже быть зафиксирована как TD-N — ссылаемся, не плодим дубли

---

## Workflow

### Шаг 1: Понять область ревью

- Изменился код → проверить новые/изменённые компоненты на соответствие теме
- Добавился новый экран → проверить UX-копирайт и состояния
- Плановый аудит → систематическое сканирование

### Шаг 2: Проверить хардкоды

```bash
# web-admin: хардкодные цвета вне theme.ts
grep -rn "#[0-9a-fA-F]\{3,6\}" web-admin/src --include="*.tsx" --include="*.css" | grep -v "theme.ts"

# web-admin: строки мимо i18n (кириллица в JSX)
grep -rn "[а-яА-Я]" web-admin/src/pages --include="*.tsx" | grep -v "i18n" | head -30

# composeApp: хардкодные цвета вне ui/theme
grep -rn "Color(0x" composeApp/src/commonMain --include="*.kt" | grep -v "ui/theme"

# composeApp: хардкодные строки в Composable
grep -rn 'Text("' composeApp/src/commonMain --include="*.kt" | head -30
```

По каждому нарушению: определить семантический смысл → сопоставить с правильным источником (тема / локаль).

### Шаг 3: Проверить UX-копирайт

Проверить каждую строку (в `i18n/` и локалях composeApp):
- Ясность: пользователь понимает что делать?
- Консистентность тона между экранами и платформами (клиент видит и приложение, и веб!)
- Русская грамматика: падежи, пунктуация, опечатки
- Empty states: объяснение почему пусто + подсказка что делать
- Сообщения об ошибках: говорят что делать дальше, не «Ошибка»
- CTA: глаголы действия («Открыть», не «Открытие»)
- Переводы: ключ есть во всех языках? (`docs/operations/TRANSLATIONS.md`)

### Шаг 4: Обновить экранные SPECы

SPEC-файлы: `docs/screens/**/*_SCREEN_SPEC.md` (правила: `docs/SCREEN_DOCUMENTATION_GUIDE.md`).

Обновить когда:
- Копирайт в коде отличается от SPEC → обновить SPEC (или поставить вопрос)
- Новые UI-состояния → задокументировать + ASCII-визуализация
- Изменилась навигация → раздел Transitions
- Пары документов нет вообще → создать (TD-016)

### Шаг 5: Сформировать Design Review Report

```markdown
## 🎨 Design Review Report

### Область
[Что проверялось]

### ✅ Соответствует
- [что в порядке]

### ⚠️ Нарушения темы/токенов
Файл: `path/to/file` (строка N)
Найдено: `color: #3b9eff` / `Color(0xFF3B9EFF)`
Должно быть: [palette-ключ темы / объект из Color.kt]

### ✍️ Проблемы UX-копирайта
Ключ: `errorMessage`
Текущий: "Ошибка загрузки"
Проблема: не говорит пользователю что делать
Предложение: "Не удалось загрузить данные. Попробуйте ещё раз."

### 📄 Обновления SPECов
- [x] docs/screens/{module}/X_SCREEN_SPEC.md — ...

### Изменений не требуется
- [проверено, в порядке]
```

---

## Смежные скиллы

| Задача | Скилл |
|---|---|
| Полный аудит дизайн-системы | `design:design-system` |
| Написание / ревью UX-копирайта | `design:ux-copy` |
| Handoff-спек для разработчиков | `design:design-handoff` |
| Общая дизайн-критика | `design:design-critique` |
| Проверка доступности (a11y) | `design:accessibility-review` |

---

## LoyaltyLoops-специфический чеклист

- [ ] Нет хардкодных цветов (web-admin: вне `theme.ts`; composeApp: вне `ui/theme/`)?
- [ ] Нет хардкодных строк (web-admin: вне `i18n/`; composeApp: вне локалей)?
- [ ] Ключи переводов есть во всех поддерживаемых языках?
- [ ] Empty states содержат и сообщение, и подсказку действия?
- [ ] Error states имеют retry?
- [ ] Тон копирайта консистентен между приложением и web-admin?
- [ ] Tier-цвета (карты лояльности) — только из `TierColors.kt`?
- [ ] Новые/изменённые SPEC-файлы отражают реальный копирайт?
- [ ] Значимые состояния экрана имеют ASCII-визуализацию в SPEC?

---

## Лаконичность комментариев и описаний

Полные правила — в `loyaltyloops-dev/SKILL.md` (раздел «Лаконичность»). Кратко:

- никаких TD-XXX / STEP_X в коде, OpenAPI, SCREEN_SPEC — история живёт в git и `ENGINEERING_CHANGELOG.md`;
- OpenAPI `description` — одно предложение по сути;
- комментарий нужен только если без него код неверно интерпретируется;
- если можно понять код без комментария — комментария быть не должно.
