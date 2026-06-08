# SynchPlay — single-image deployment.
# Builds the Vue frontend, bundles it into the Spring Boot jar (served same-origin),
# and runs one service. Needs an external PostgreSQL (set DB_* env vars at runtime).

# ---------- Stage 1: build the Vue frontend ----------
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend-vue/package*.json ./
RUN npm ci
COPY frontend-vue/ ./
RUN npm run build          # -> /fe/dist

# ---------- Stage 2: build the Spring Boot jar (frontend bundled in) ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /be
COPY backend-springboot/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend-springboot/ ./
# bundle the built SPA into the jar's static resources (served at /)
COPY --from=frontend /fe/dist/ src/main/resources/static/
RUN mvn -q -B clean package -DskipTests

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /be/target/*.jar app.jar
# graph CSVs the backend imports on first boot
COPY ProcessedData/mini_nodes.csv ProcessedData/mini_edges.csv /app/ProcessedData/
ENV SPRING_PROFILES_ACTIVE=prod \
    NODES_CSV=/app/ProcessedData/mini_nodes.csv \
    EDGES_CSV=/app/ProcessedData/mini_edges.csv \
    UPLOADS_DIR=/app/uploads
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
