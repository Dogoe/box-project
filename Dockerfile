
# ---- Build stage ----
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
# Expose app port + debug port
EXPOSE 8080 5005

# Enable remote debugging on port 5005
ENTRYPOINT ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005","-jar","app.jar"]
# ENTRYPOINT ["java", "-jar", "app.jar"]