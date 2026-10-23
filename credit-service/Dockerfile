FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 7200
ENTRYPOINT ["java", "-jar", "app.jar"]
