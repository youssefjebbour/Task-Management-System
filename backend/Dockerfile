FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S taskflow && adduser -S taskflow -G taskflow
WORKDIR /app
RUN mkdir uploads && chown taskflow:taskflow uploads
COPY --from=build --chown=taskflow:taskflow /build/target/taskflow-api-*.jar app.jar
USER taskflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
