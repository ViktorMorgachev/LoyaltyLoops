# LoyaltyLoops ♾️

**LoyaltyLoops** — это современная, кроссплатформенная система управления лояльностью (Loyalty Management System), построенная на стеке **Kotlin Multiplatform**.

Проект объединяет серверную логику, мобильные приложения (Android/iOS) для клиентов и сотрудников, а также веб-панель администратора в едином монорепозитории.

## 📚 Документация

*   **[🛠 Руководство по Сборке и Деплою (Deployment)](docs/DEPLOYMENT.md)** — Как запустить проект локально и на сервере.
*   **[🔄 Архитектура и Флоу (Technical Flow)](docs/LOYALTY_FLOW.md)** — Как это работает под капотом (QR, сокеты, транзакции).
*   **[💼 Возможности для Партнеров (Features)](docs/PARTNER_GUIDE.md)** — Описание бизнес-функционала (CRM, Стратегии, Аналитика).

---

## 🚀 Технологический стек

### 📱 Mobile (Android & iOS) — `:composeApp`
*   **UI**: Compose Multiplatform (Material 3)
*   **Navigation**: Voyager
*   **DI**: Koin
*   **Network**: Ktor Client
*   **Maps**: Yandex Maps Mobile
*   **Images**: Kamel
*   **Permissions**: Moko Permissions

### 🛠 Backend — `:server`
*   **Framework**: Ktor Server (Netty)
*   **Database**: PostgreSQL
*   **ORM**: Exposed
*   **Real-time**: WebSockets
*   **Auth**: JWT + OTP (SMS)
*   **Docs**: Swagger UI

### 🌐 Web Admin — `:web-admin`
*   **Framework**: React 19
*   **Language**: TypeScript
*   **Build Tool**: Vite
*   **UI Kit**: Material UI (MUI v6)
*   **State**: React Hooks + Axios

### 📦 Shared — `:shared`
*   **Kotlin Multiplatform**: Общая бизнес-логика, DTO, валидация и утилиты, используемые на сервере и в мобильных клиентах.

---

## 📂 Структура проекта

| Модуль | Описание |
| :--- | :--- |
| `composeApp` | Единое приложение для **Клиентов**, **Кассиров** и **Владельцев**. Интерфейс адаптируется под роль. |
| `server` | REST API и WebSocket сервер. Управляет транзакциями, пользователями и пушами. |
| `web-admin` | Панель управления для владельцев бизнеса и супер-админов платформы. |
| `shared` | Общий код (Data Transfer Objects, константы, парсеры). |

---

## 🚦 Быстрый старт

### Предварительные требования
*   JDK 17+
*   Android Studio (для Android) / Xcode (для iOS)
*   Node.js 20+ (для Web)
*   Docker (для базы данных)

### 1. Запуск Базы Данных
Проект использует Docker Compose для поднятия PostgreSQL.
```bash
docker-compose up -d
```

### 2. Запуск Сервера
```bash
./gradlew :server:run
```
Сервер запустится на `http://0.0.0.0:8080`.
Swagger документация доступна по адресу: `http://localhost:8080/swagger`

### 3. Запуск Web Admin
```bash
cd web-admin
npm install
npm run dev
```
Панель будет доступна по адресу `http://localhost:3000`.

### Если занят порт веба
```bash
lsof -i :3000 -t | xargs kill -9
```

### Если занят порт бэка
```bash
lsof -i :8080 -t | xargs kill -9
```

### 4. Запуск Мобильного приложения
**Android:**
Откройте проект в Android Studio и запустите конфигурацию `composeApp`. Или через терминал:
```bash
./gradlew :composeApp:installDebug
adb shell monkey -p io.loyaltyloop.app 1
```
### 5. Билд релизного приложения
```bash
./gradlew :composeApp:assembleLoyaltyloopRelease -Penv=prod
```

### 5. Билд релизного приложения в сторы
```bash
./gradlew bundleRelease -Penv=prod
```


**iOS:**
1. Убедитесь, что установлены CocoaPods.
2. Перейдите в `iosApp` и установите поды (если требуется, обычно Gradle делает это автоматически при сборке через KMP плагин, но иногда требуется ручной `pod install`).
3. Запустите через Xcode или Fleet.

---

## 🔑 Ключевые возможности

1.  **Мульти-ролевая система**: Одно приложение поддерживает роли "Клиент" (кошелек), "Кассир" (терминал) и "Партнер".
2.  **QR-процессинг**: Начисление и списание бонусов через сканирование динамических QR-кодов.
3.  **Real-time обновления**: Баланс пользователя обновляется мгновенно через WebSockets.
4.  **Геолокация**: Поиск ближайших торговых точек на карте.
5.  **Администрирование**: Полный цикл управления партнерами, филиалами и персоналом через веб-интерфейс.

---

## 📄 Лицензия

Copyright (c) 2025 LoyaltyLoop. All Rights Reserved.

**ВНИМАНИЕ**: Вся информация, содержащаяся здесь, является и остается собственностью LoyaltyLoop.

Интеллектуальные и технические концепции, содержащиеся здесь, являются собственностью LoyaltyLoops и защищены законом об авторском праве.

Распространение этой информации или воспроизведение этого материала **строго запрещено** без предварительного письменного разрешения от LoyaltyLoop.

Подробнее см. файл [LICENSE](LICENSE).
