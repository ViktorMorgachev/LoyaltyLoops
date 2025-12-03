# LoyaltyLoop ♾️

**LoyaltyLoop** — это современная, кроссплатформенная система управления лояльностью (Loyalty Management System), построенная на стеке **Kotlin Multiplatform**.

Проект объединяет серверную логику, мобильные приложения (Android/iOS) для клиентов и сотрудников, а также веб-панель администратора в едином монорепозитории.

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
lsof -nP -iTCP:3000 | grep LISTEN
```

### Если занят порт бэка
```bash
lsof -nP -iTCP:8000 | grep LISTEN
```

### 4. Запуск Мобильного приложения
**Android:**
Откройте проект в Android Studio и запустите конфигурацию `composeApp`. Или через терминал:
```bash
./gradlew :composeApp:installDebug
```

**iOS:**
1. Убедитесь, что установлены CocoaPods.
2. Перейдите в `iosApp` и установите поды (если требуется, обычно Gradle делает это автоматически при сборке через KMP плагин, но иногда требуется ручной `pod install`).
3. Запустите через Xcode или Fleet.

---

## 📦 Сборка и Деплой (Production)

Подробная инструкция по настройке переменных окружения и сборке для продакшена (Railway/Docker) находится в файле:
👉 **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**

---

## 🔑 Ключевые возможности

1.  **Мульти-ролевая система**: Одно приложение поддерживает роли "Клиент" (кошелек), "Кассир" (терминал) и "Партнер".
2.  **QR-процессинг**: Начисление и списание бонусов через сканирование динамических QR-кодов.
3.  **Real-time обновления**: Баланс пользователя обновляется мгновенно через WebSockets.
4.  **Геолокация**: Поиск ближайших торговых точек на карте.
5.  **Администрирование**: Полный цикл управления партнерами, филиалами и персоналом через веб-интерфейс.

---

## 📄 Лицензия

Этот проект распространяется под лицензией **MIT License**.

Это означает, что вы можете свободно использовать, менять и распространять код, при условии **сохранения авторства** (ссылки на репозиторий и указания автора).

Подробнее см. файл [LICENSE](LICENSE).
