# 🚀 Руководство по Сборке и Деплою (Deployment Guide)

Этот документ описывает, как настроить и собрать проект для разных окружений (Dev, Stage, Prod).

---

## 1. 📱 Mobile (Android & iOS)

В проекте используется плагин **BuildConfig** для автоматической подмены API URL в зависимости от типа сборки.
Конфигурация находится в `composeApp/build.gradle.kts` в блоке `buildConfig { ... }`.

### Сборка для разных окружений

Окружение задаётся параметром `-Penv=...`. Без параметра (`dev`) сборка указывает на **stage-сервер** (не на localhost).

**Примеры команд** (запускать из корня репозитория; `cd` в начале позволяет запускать их и через ▶ в IDE прямо из этого файла):

```bash
# 🧪 DEV/STAGE (https://server-loyalityloop-stage.up.railway.app) - По умолчанию
cd "$(git rev-parse --show-toplevel)" && ./gradlew :composeApp:assembleDebug
```
```bash
# 🚀 PROD (https://api.loyaltyloops.app)
cd "$(git rev-parse --show-toplevel)" && ./gradlew :composeApp:assembleRelease -Penv=prod
```
```bash
# 🧪 STAGE явно
cd "$(git rev-parse --show-toplevel)" && ./gradlew :composeApp:assembleDebug -Penv=stage
```

**Где менять URL?**
В файле `composeApp/build.gradle.kts` (блок `buildConfig`):
```kotlin
val (serverUrl, webUrl) = when (activeEnv) {
    "prod" -> "https://api.loyaltyloops.app" to "https://loyaltyloops.app"
    "stage" -> "https://server-loyalityloop-stage.up.railway.app" to "https://loyalityloop-beta.up.railway.app"
    else ->  "https://server-loyalityloop-stage.up.railway.app" to "https://loyalityloop-beta.up.railway.app"
}
```

---

## 2. 🖥 Server (Backend)

Сервер конфигурируется через **Переменные окружения (Environment Variables)**.
Файл настроек: `server/src/main/resources/application.conf`.

### Переменные для Railway / Docker

При деплое на Railway или любой VPS обязательно задайте эти переменные:

| Переменная | Описание | Пример значения |
| :--- | :--- | :--- |
| `PORT` | Порт запуска (Railway ставит сам) | `8080` |
| `JDBC_DATABASE_URL` | URL подключения к БД | `jdbc:postgresql://host:5432/db` |
| `JDBC_DATABASE_USERNAME` | Пользователь БД | `postgres` |
| `JDBC_DATABASE_PASSWORD` | Пароль БД | `secret` |
| `JWT_SECRET` | **Секретный ключ** для токенов | `dlinnaya_strolka_simvolov_123` |
| `JWT_ISSUER` | Ваш домен (кто выдал токен) | `https://api.loyaltyloops.app` |
| `JWT_AUDIENCE` | Домен API | `https://api.loyaltyloops.app` |
| `WEB_BASE_URL` | Ссылка на веб-админку (для писем) | `https://loyaltyloops.app` |
| `CORS_ALLOWED_HOSTS` | Браузерные origin'ы через запятую (см. `cors.allowedHosts`) | `loyaltyloops.app,www.loyaltyloops.app,loyalityloop-beta.up.railway.app` |
| `ADMIN_DEFAULT_PIN` | PIN-код для супер-админа | `1111` |

**Dockerfile**:
В корне проекта лежит `Dockerfile`, готовый для деплоя. Railway автоматически подхватит его.

---

## 3. 🌐 Web Admin (Frontend)

Веб-админка конфигурируется через `.env` файлы (стандарт Vite).

### Локальная разработка
Файл `.env` (не комитится в git):
```properties
VITE_API_URL=http://localhost:8080
```
```
docker build \
--build-arg VITE_API_URL=https://api.stage \
--build-arg VITE_APP_ENV=stage \
--build-arg VITE_YMAPS_API_KEY=xxx \
--build-arg VITE_SHOW_PLAY_LINKS=true \
-t web-admin
```

### Продакшн
Railway автоматически собирает проект через `npm run build`.
Vite "запекает" переменные в JS код во время сборки.

Убедитесь, что на сервере сборки (Railway) задана переменная:
`VITE_API_URL` = `https://api.loyaltyloops.app` (адрес вашего бэкенда)

**Варианты деплоя:**
1. **Вместе с бэкендом (Monolith)**: Сервер раздает статику из папки `web-admin/dist`. (Требует настройки Ktor).
2. **Отдельно (Рекомендуется)**: Railway сервис для фронтенда (Static Site) или Docker c Nginx.

