# 🚀 Руководство по Сборке и Деплою (Deployment Guide)

Этот документ описывает, как настроить и собрать проект для разных окружений (Dev, Stage, Prod).

---

## 1. 📱 Mobile (Android & iOS)

В проекте используется плагин **BuildConfig** для автоматической подмены API URL в зависимости от типа сборки.
Конфигурация находится в `composeApp/build.gradle.kts` в блоке `buildConfig { ... }`.

### Сборка для разных окружений

По умолчанию используется окружение `dev` (Localhost). Чтобы собрать версию для продакшена, передайте параметр `-Penv=prod`.

**Примеры команд:**

```bash
# 🛠 DEV (Localhost: 10.0.2.2:8080) - По умолчанию
./gradlew :composeApp:assembleDebug

# 🚀 PROD (https://api.loyaltyloop.kg)
./gradlew :composeApp:assembleRelease -Penv=prod

# 🧪 STAGE (https://api-test.loyaltyloop.kg)
./gradlew :composeApp:assembleDebug -Penv=stage
```

**Где менять URL?**
В файле `composeApp/build.gradle.kts`:
```kotlin
val serverUrl = when(activeEnv) {
    "prod" -> "https://api.loyaltyloop.kg"
    "stage" -> "https://api-test.loyaltyloop.kg"
    else -> "http://10.0.2.2:8080"
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
| `JWT_ISSUER` | Ваш домен (кто выдал токен) | `https://api.loyaltyloop.kg` |
| `JWT_AUDIENCE` | Домен API | `https://api.loyaltyloop.kg` |
| `WEB_BASE_URL` | Ссылка на веб-админку (для писем) | `https://admin.loyaltyloop.kg` |
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

### Продакшн
Railway автоматически собирает проект через `npm run build`.
Vite "запекает" переменные в JS код во время сборки.

Убедитесь, что на сервере сборки (Railway) задана переменная:
`VITE_API_URL` = `https://api.loyaltyloop.kg` (адрес вашего бэкенда)

**Варианты деплоя:**
1. **Вместе с бэкендом (Monolith)**: Сервер раздает статику из папки `web-admin/dist`. (Требует настройки Ktor).
2. **Отдельно (Рекомендуется)**: Railway сервис для фронтенда (Static Site) или Docker c Nginx.

