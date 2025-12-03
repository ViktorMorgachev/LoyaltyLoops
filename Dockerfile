# --- Stage 1: Build ---
FROM gradle:7.6.0-jdk17 AS build

# Копируем всё содержимое проекта
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Собираем ТОЛЬКО серверную часть (но Gradle сам подтянет shared)
# Используем installDist, чтобы создать папку с запускаемыми скриптами
RUN ./gradlew :server:installDist --no-daemon

# --- Stage 2: Run ---
FROM openjdk:17-slim

# Создаем рабочую директорию
WORKDIR /app

# Копируем результаты сборки из первого этапа
# Путь зависит от имени твоего модуля, обычно это server
COPY --from=build /home/gradle/src/server/build/install/server /app/

# Открываем порт (Railway сам прокинет его в ENV PORT, но Ktor должен его читать)
EXPOSE 8080

# Запускаем скрипт
# ВАЖНО: Проверь имя своего скрипта в server/build.gradle.kts (application { mainClass = ... })
# Обычно имя скрипта совпадает с именем модуля (server)
CMD ["./bin/server"]