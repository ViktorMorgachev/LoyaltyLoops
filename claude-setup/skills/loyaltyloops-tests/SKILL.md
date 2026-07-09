---
name: loyaltyloops-tests
description: "Test maintenance module for LoyaltyLoops. Use whenever code changes touch tested behavior, tests fail, or new tests are needed. Triggers: 'почини тесты', 'тесты упали', 'обнови тесты', 'актуализируй тесты', 'напиши тест', 'fix tests', 'tests failing', 'update tests', 'add test'. Always use this skill after changing API contracts, DTOs, validation, or business logic — it knows the test infrastructure, helpers, and how to tell a stale test from a real bug. Do NOT comment out failing tests."
---

# LoyaltyLoops Test Maintenance Module

Тесты — такие же клиенты API, как мобилка и web-admin. Изменился контракт — тесты обновляются **в том же коммите**, а не комментируются «чтобы не мешали».

Главное правило: **упавший тест — это вопрос, а не помеха.** Сначала ответь, кто неправ — тест или код. Подгонять тест под баг запрещено.

---

## Инфраструктура тестов сервера

| Что | Где / Как |
|---|---|
| Тесты | `server/src/test/kotlin/io/loyaltyloop/server/` |
| Окружение | `TestUtils.configureTestEnv()`: H2 in-memory (`MODE=PostgreSQL`, `DB_CLOSE_DELAY=-1` — БД живёт весь JVM-прогон), тестовые JWT-ключи, `features.enableTestSupport=true` |
| Схема БД | Из Exposed table objects (`DatabaseFactory` → не-Postgres ветка). Flyway в тестах НЕ работает — Postgres-специфичный SQL миграций тесты не проверяют |
| HTTP-клиент | `createJsonClient()` |
| Авторизация | `HttpClient.registerAndLogin()` — send-code → OTP → login. Требования зашиты в хелпер: заголовок `X-Timezone-Id` (валидная таймзона поддерживаемой страны, напр. `Asia/Bishkek`), KG-номер из `generateValidPhone()` |
| OTP в тестах | `/auth/send-code` возвращает `debugCode` (реальный код) при console-провайдере SMS; парсинг — `extractOtpCode()` |
| Запуск | `./gradlew :server:test`; один класс: `--tests "io.loyaltyloop.server.XxxTest"` |
| Отчёт при падении | `server/build/test-results/test/TEST-*.xml` — там полное сообщение с телом ответа |

---

## Workflow: изменился код

### Шаг 1: Определить затронутые контракты

```bash
git diff --name-only HEAD~1 HEAD -- server/ shared/
```

Триггеры обязательного обновления тестов:
- изменена сигнатура/поведение функций из `utils/` (валидация, форматирование)
- изменён API-контракт: новый обязательный заголовок, поле DTO, код ошибки
- изменена бизнес-логика: `LoyaltyCalculator`, `TransactionService`, `RatingService`
- изменена схема БД / repository-методы

### Шаг 2: Найти затронутые тесты

```bash
grep -rln "ИмяФункции\|ИмяКласса" server/src/test/kotlin
```

Если тестов на изменённое поведение нет — это сигнал написать их, а не повод выдохнуть.

### Шаг 3: Обновить/дописать тесты

- Контракт «функция бросает `LoyaltyException`» → `assertFailsWith<LoyaltyException>` + проверка `ex.code` (`AppErrorCode.*`)
- Новый обязательный заголовок/поле → обновить хелперы в `TestUtils.kt` (они — единственная точка правды для auth-флоу)
- Числовые ожидания сверять с фикстурами в том же файле, а не с комментариями

### Шаг 4: Gate

```bash
./gradlew :server:test
```

Зелёный прогон — обязательное условие завершения задачи. Detekt-исключения в тестах не копить: пустой тест-заглушка хуже отсутствующего.

---

## Workflow: тест упал

### Шаг 1: Прочитать полное сообщение

Консоль Gradle обрезает причину. Смотри `server/build/test-results/test/TEST-<класс>.xml` — там статус и тело ответа сервера.

### Шаг 2: Классифицировать

| Симптом | Диагноз | Действие |
|---|---|---|
| Тест написан под старую сигнатуру (напр. `assertNull(Unit)`) | Протухший тест | Переписать под актуальный контракт |
| Ожидание расходится с фикстурой в том же файле | Протухший тест | Синхронизировать с фикстурой |
| Setup-хелпер получает 400 (новый заголовок/валидация) | Протухшая инфраструктура | Обновить `TestUtils`, как обновили бы мобилку |
| Код ведёт себя не так, как задумано (см. спеки, имена полей) | **Реальный баг** | Чинить код, тест не трогать |
| Падает только при параллельном запуске / зависит от порядка | Флак: общий H2 (`DB_CLOSE_DELAY=-1`), коллизии `generateValidPhone` | Изолировать данные теста |

### Шаг 3: Никогда

- ❌ не комментировать упавший тест (история: закомментированный `registerAndLogin` три месяца скрывал сломанный auth-флоу и обход анти-перебора OTP)
- ❌ не подгонять ожидание под фактическое значение, не поняв, почему оно такое
- ❌ не помечать `@Ignore` без записи в `docs/TECH_DEBT.md` с причиной и планом

---

## Что покрывать в первую очередь (пока покрытие низкое)

1. **Деньги**: `LoyaltyCalculator`, `TransactionService` (списание/начисление, округление, tier-переходы). Конкурентный тест на двойное списание — TD-019: два параллельных `POST /terminal/process` на весь баланс → одна успешна, вторая `INVALID_AMOUNT`, баланс ≥ 0
2. **Access control**: роли, `AccessControlService`, заблокированный партнёр/карта
3. **Валидация входа**: телефоны, суммы, QR-подписи
4. **Контракты ошибок**: `AppErrorCode` + HTTP-статус (маппинг в `ErrorHandler.kt`)

---

## Чеклист перед коммитом

- [ ] `./gradlew :server:test` зелёный?
- [ ] Изменённый контракт покрыт тестом (позитив + негатив)?
- [ ] Хелперы `TestUtils` соответствуют актуальным требованиям API?
- [ ] Нет новых `@Ignore`/закомментированных тестов без TD-записи?
- [ ] Числовые ожидания сверены с фикстурами, а не с комментариями?
- [ ] Если найден реальный баг — исправлен код, а в `ENGINEERING_CHANGELOG.md` есть запись?

---

## Лаконичность

Полные правила — в `loyaltyloops-dev/SKILL.md`. В тестах комментарий уместен только там, где неочевиден выбор данных (например, «+996 + 8 цифр — слишком короткий»). Имена тестов — самодокументация: `` `validatePhoneNumber fails for unknown country code` ``.
