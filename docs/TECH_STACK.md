# TECH_STACK.md

Используемые технологии LoyaltyLoops. Обновлять при добавлении/замене значимой зависимости.

## Модули

| Модуль | Назначение |
|---|---|
| `composeApp` | Мобильное приложение (Android/iOS) для клиентов, кассиров и владельцев |
| `server` | REST API + WebSocket сервер |
| `shared` | Общий KMP-код: DTO, константы, валидация, утилиты |
| `web-admin` | Панель управления (владельцы бизнеса, супер-админы) |

## Backend (`server`)

| Слой | Технология |
|---|---|
| Framework | Ktor Server (Netty) |
| DI | Koin |
| БД | PostgreSQL |
| ORM | Exposed |
| Connection pool | HikariCP |
| Real-time | Ktor WebSockets |
| Auth | JWT (auth0 java-jwt) + OTP (SMS: Prelude), Telegram auth |
| Email | Resend API |
| HTTP-клиент | OkHttp |
| Кэш | Redis (Jedis pool) |
| API docs | OpenAPI + Swagger UI (`server/src/main/resources/openapi/documentation.yaml`) |

## Mobile (`composeApp`)

| Слой | Технология |
|---|---|
| UI | Compose Multiplatform (Material 3) |
| Навигация | Voyager |
| DI | Koin |
| Network | Ktor Client |
| Карты | Yandex Maps Mobile |
| Изображения | Kamel |
| Permissions | Moko Permissions |
| Локальное хранилище | SQLDelight |

## Web Admin (`web-admin`)

| Слой | Технология |
|---|---|
| Framework | React 19 + TypeScript |
| Сборка | Vite |
| UI Kit | Material UI (MUI v6), тема — `src/theme.ts` |
| HTTP | Axios (`src/api/axiosConfig.ts`) |
| i18n | i18next (`src/i18n/`) |
| Состояние | React Hooks |

## Инфраструктура

| Что | Технология |
|---|---|
| Контейнеризация | Docker (multi-stage: gradle → temurin), docker-compose для локальной PostgreSQL |
| Статический анализ | detekt (все Kotlin-модули, конфиг `config/detekt/detekt.yml`) |
| Сборка | Gradle 8.7 (wrapper), JDK 17 |
