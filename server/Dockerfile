FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jdk-jammy
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/server/build/install/server/ /app/
WORKDIR /app/bin
CMD ["./server"]

