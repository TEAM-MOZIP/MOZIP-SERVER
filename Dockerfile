# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Docker Compose healthcheck가 사용하는 curl은 이 이미지에 기본 포함되어 있지 않아 명시적으로 설치한다.
RUN apk add --no-cache curl

COPY --from=build /workspace/build/libs/server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
