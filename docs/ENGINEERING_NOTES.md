# ENGINEERING_NOTES.md

Архитектурные решения и заметки. Фиксировать решение, контекст и последствия — кратко.

---

## Время — всегда UTC

Вся система живёт в UTC на трёх уровнях: PostgreSQL (`ALTER DATABASE ... SET timezone TO 'UTC'` / `PGTZ=UTC`), HikariCP (`connectionInitSql = "SET TIME ZONE 'UTC'"`), JVM (`-Duser.timezone=UTC` в Dockerfile). Подробности: `operations/POSTGRES_UTC_SETUP.md`.

## Схема БД — Flyway

Миграции: `server/src/main/resources/db/migration/V{N}__description.sql`. `V1__baseline.sql` зеркалит схему на момент перехода; на существовавших БД он пропускается (`baselineOnMigrate=true` помечает их версией 1), выполняются только V2+. Изменение схемы = обновить Exposed table object **и** написать миграцию — они обязаны совпадать. Тесты (H2) миграции не используют: схема создаётся из table objects (`DatabaseFactory`), поэтому Postgres-специфичный SQL в миграциях допустим.

## Один бэкенд — три роли клиента

`composeApp` — единое приложение для Клиента, Кассира и Владельца; интерфейс адаптируется под роль. Access control проверяется на сервере (`AccessControlService`), роли — `UserRole`.

## Real-time

Баланс карт и чат поддержки — через WebSockets (`/ws/cards`, `/ws/support/*`). Токен передаётся query-параметром и валидируется до регистрации сессии.

## Docker-сборка сервера

Multi-stage: `gradle:*-jdk17` → `eclipse-temurin:17`. Сборка флагом `-PserverBuild=true` отключает Android/iOS таргеты shared-модуля. Версия Gradle в образе обязана совпадать с `gradle-wrapper.properties`, иначе wrapper качает дистрибутив на каждой сборке (см. TD-018 — падение деплоя 2026-07-07).
