# --- Этап 1: Сборка (Build) ---
FROM gradle:8.5.0-jdk17 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# 1. Даем права на выполнение скрипта (важно!)
RUN chmod +x ./gradlew

# 2. Запускаем сборку с флагом -PserverBuild=true
# Это отключит Android и iOS в shared модуле, и сборка не упадет из-за отсутствия SDK
RUN ./gradlew :server:installDist --no-daemon -PserverBuild=true

# --- Этап 2: Запуск (Run) ---
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /home/gradle/src/server/build/install/server /app/

EXPOSE 8080

RUN chmod +x ./bin/server
CMD ["./bin/server"]