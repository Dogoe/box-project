# ---- Build stage ----
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY . .
# Tests run in CI/locally; the image build only packages the app.
RUN gradle bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Times in emails and the admin (order dates) use the store's local time.
RUN apk add --no-cache tzdata && addgroup -S app && adduser -S app -G app
ENV TZ=America/Tijuana
COPY --from=builder /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
# Fits a small (512 MB) container. Override with JAVA_OPTS in the hosting panel if needed.
# For debugging locally, docker-compose.yml adds the debug agent through JAVA_TOOL_OPTIONS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=America/Tijuana"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
