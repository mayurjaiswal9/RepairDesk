# RepairDesk backend — multi-stage Docker build (REL-003).
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Download dependencies first (better layer caching). chmod: Windows uploads drop the execute bit.
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src src
# maven.test.skip skips test compilation too (-DskipTests still compiles tests).
RUN ./mvnw package -Dmaven.test.skip=true -q && \
    cp target/repairdesk-*.jar /workspace/app.jar

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S repairdesk && adduser -S repairdesk -G repairdesk && \
    mkdir -p /app/uploads && \
    chown -R repairdesk:repairdesk /app
USER repairdesk

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
