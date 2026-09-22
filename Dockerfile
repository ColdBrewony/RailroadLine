# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Cache Gradle dependencies separately from source changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser

# DataSeeder reads data/raw/lines/*.json and the station-coordinates CSVs
# relative to the working directory at every startup (read-only source data,
# re-seeded into H2 on each boot), so it has to ship inside the image.
COPY --from=build /workspace/build/libs/*.jar app.jar
COPY data/raw ./data/raw

RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
