# --- Этап 1: Сборка (Build) ---
# Версия Gradle в образе обязана совпадать с gradle-wrapper.properties (8.7):
# используем предустановленный gradle вместо ./gradlew, чтобы wrapper
# не скачивал дистрибутив на каждой сборке (падало по таймауту на CI).
FROM gradle:8.7.0-jdk17 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Переопределяем -Xmx16g из gradle.properties: столько памяти на CI-билдере нет
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"

# Флаг -PserverBuild=true отключает Android/iOS таргеты shared-модуля,
# сборка не требует Android SDK / Xcode
RUN gradle :server:installDist --no-daemon -PserverBuild=true

# --- Этап 2: Запуск (Run) ---

FROM eclipse-temurin:17-jdk-jammy
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

WORKDIR /app

COPY --from=build /home/gradle/src/server/build/install/server /app/

EXPOSE 8080

RUN chmod +x ./bin/server
CMD ["./bin/server"]
