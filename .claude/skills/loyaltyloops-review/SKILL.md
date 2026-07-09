---
name: loyaltyloops-review
description: "Code review module for LoyaltyLoops. Use for reviewing any code changes before committing or merging. Triggers: 'сделай ревью', 'проверь код', 'review this', 'review before merge', 'что ты думаешь о коде'. Always use this skill for code review in LoyaltyLoops — it runs a structured two-phase review where Claude analyzes first, then presents findings so the developer can learn and add their own observations. Do NOT skip review before merging non-trivial changes."
---

# LoyaltyLoops Code Review Module

Двухфазное ревью: Claude делает автоматизированный анализ → представляет результаты → Viktor добавляет наблюдения → объединяем в финальный `review.md`.

Цель — не просто найти баги, а сделать каждое ревью возможностью научиться. Claude объясняет *почему* что-то важно, а не только *что* не так.

---

## Область ревью

1. **Diff-анализ** — что реально изменилось и зачем
2. **Соответствие спеку** — реализация совпадает со SPEC/task-документом?
3. **Архитектура** — routes тонкие? логика в services? DTO в shared? ошибки через `LoyaltyException`?
4. **Безопасность** — валидация input, проверки auth/access (`AccessControlService`), нет хардкодных секретов, нет новых дыр из класса TD-001/002/003
5. **Компиляция** — `./gradlew :server:build` для Kotlin; `npx tsc --noEmit` для web-admin
6. **Edge cases** — null, empty states, error paths, конкурентность (особенно деньги/баланс!)
7. **Производительность** — N+1 запросы, отсутствие индексов, лишние re-renders/recompositions
8. **Технический долг** — пробежать `docs/TECH_DEBT.md`. Diff не должен открывать новый TD без фиксации в реестре. Если diff закрывает TD — запись помечена `~~TD-N~~ ✅ ЗАКРЫТ` со ссылкой на ENGINEERING_CHANGELOG

---

## Phase 1: Автоматизированное ревью

### 1. Получить diff

```bash
git diff HEAD~1 HEAD          # последний коммит
git diff main...feature-branch  # diff ветки
```

### 2. Определить затронутые области

| Файлы | Что проверять |
|---|---|
| `server/routes/` | Тонкость handlers, access control, rate limit, `documentation.yaml` |
| `server/service/` | Бизнес-логика, транзакции БД, блокировки при деньгах |
| `server/database/` | Совместимость с `createMissingTablesAndColumns` (nullable/default!) |
| `shared/` | Обратная совместимость DTO с мобильными клиентами |
| `composeApp/` | Цвета из theme, строки из локалей, состояния экрана |
| `web-admin/src/` | tsc чисто, нет новых `any`, тема MUI, i18n |
| `docs/` | Синхронизированы ли с изменениями кода |

### 3. Запустить gates

```bash
./gradlew :server:build :server:test     # если менялся Kotlin
cd web-admin && npx tsc --noEmit          # если менялся web-admin
```

### 4. Проверить соответствие спеку

Для изменённых экранов — `docs/screens/**/*_SCREEN_SPEC.md` (если пары нет — зафиксировать TD-016-нарушение и предложить создать):
- Все состояния (loading, empty, error, ready) обработаны
- Навигация совпадает со спеком
- Access control корректен

### 5. Сформировать Phase 1 Report

```markdown
## 🔍 Phase 1: Automated Review

### Что изменилось
[1-3 предложения]

### ✅ Выглядит хорошо
- [что сделано правильно + почему это хорошая практика]

### ⚠️ Найденные проблемы
**[Серьёзность: Critical / Warning / Suggestion]**
Файл: `path/to/file.kt` (строка N)
Проблема: [что не так]
Почему важно: [объяснение для обучения]
Исправление: [конкретное предложение]

### 📚 Learning Moments
3 ключевых концепции, проиллюстрированных этим diff:
1. [концепция]: [объяснение]
2. [концепция]: [объяснение]
3. [концепция]: [объяснение]

### Gates: server build ✅/❌ | tsc ✅/❌
### Spec Compliance: ✅ / ⚠️ [расхождения]
### TECH_DEBT: [новый TD? закрытый TD? записи синхронизированы?]
```

---

## Phase 2: Checkpoint для разработчика

После представления Phase 1 — **пауза и приглашение Viktor'у**:

---
**🧑‍💻 Твоя очередь!**

Я закончил свой анализ выше. Прежде чем финализировать, посмотри diff сам:

```bash
git diff HEAD~1 HEAD
```

На что обратить внимание:
- Логика работает end-to-end?
- Есть бизнес-логика, которая кажется странной (даже если синтаксически верна)?
- Что бы ты сделал иначе?
- Что нового ты узнал из раздела «Learning Moments»?

Добавь свои наблюдения в ответ, и я объединю оба взгляда в финальный `review.md`.

---

## Phase 3: Финальный документ ревью

После обратной связи от Viktor'а — создать `docs/reviews/review-{date}-{feature}.md`:

```markdown
# Review: {Feature Name}
Date: {date}
Reviewer: Claude + Viktor

## Summary
[Что проверено, 2-3 предложения]

## Automated Findings
[Сжатый вывод Phase 1]

## Developer Notes
[Наблюдения Viktor'а]

## Combined Verdict
**Status: ✅ APPROVED / ⚠️ NEEDS FIXES / ❌ BLOCKED**

Required fixes before merge:
- [ ] ...

Nice-to-haves (non-blocking):
- [ ] ...

## What We Learned
[Ключевые выводы]
```

Зафиксировать:
```bash
git add docs/reviews/review-{date}-{feature}.md
git commit -m "docs: add code review for {feature}"
```

---

## LoyaltyLoops-специфический чеклист

- [ ] Новый API endpoint добавлен в `documentation.yaml`?
- [ ] Изменение схемы БД совместимо с `createMissingTablesAndColumns` (nullable/default)?
- [ ] Денежные/балансовые операции — в одной транзакции, с блокировкой строки?
- [ ] Ошибки — через `LoyaltyException(AppErrorCode.*)`, маппинг в `ErrorHandler.kt` дополнен?
- [ ] DTO в `shared` обратно совместимы (мобильные клиенты в проде!)?
- [ ] web-admin: нет новых `any`, тема MUI, строки в i18n?
- [ ] composeApp: цвета из theme, строки из локалей, все состояния экрана?
- [ ] Error state + retry для каждого data-fetching экрана?
- [ ] Нет `println` в server-коде?
- [ ] `docs/TECH_DEBT.md` синхронизирован: новый TD зафиксирован, закрытый — `~~TD-N~~ ✅ ЗАКРЫТ`?

---

## Лаконичность комментариев и описаний

Полные правила — в `loyaltyloops-dev/SKILL.md` (раздел «Лаконичность»). Кратко:

- никаких TD-XXX / STEP_X в коде, OpenAPI, SCREEN_SPEC — история живёт в git и `ENGINEERING_CHANGELOG.md`;
- OpenAPI `description` — одно предложение по сути;
- комментарий нужен только если без него код неверно интерпретируется;
- если можно понять код без комментария — комментария быть не должно.
