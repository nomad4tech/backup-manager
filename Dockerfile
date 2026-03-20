# ─── Stage 1: Build frontend ───────────────────────────────────────────────
FROM node:20-alpine AS frontend-builder

WORKDIR /frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ─── Stage 2: Build backend ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

WORKDIR /app

# Configure Maven to use GitHub Packages if GITHUB_TOKEN is provided
RUN if [ -n "${GITHUB_TOKEN}" ]; then \
      mkdir -p /root/.m2 && printf '<settings>\n  <servers>\n    <server>\n      <id>github</id>\n      <username>%s</username>\n      <password>%s</password>\n    </server>\n  </servers>\n</settings>\n' "${GITHUB_ACTOR}" "${GITHUB_TOKEN}" > /root/.m2/settings.xml; \
    fi

COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src/ ./src/

COPY --from=frontend-builder /frontend/dist ./src/main/resources/static/

RUN mvn package -DskipTests -q

# ─── Stage 3: Final image ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN mkdir -p /data /app/backups && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown appuser:appgroup /data /app/backups

COPY --from=backend-builder /app/target/backup-manager-*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]