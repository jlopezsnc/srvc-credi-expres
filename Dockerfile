# syntax=docker/dockerfile:1

# ---------- Etapa de build (Java 21) ----------
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /app

# Primero solo lo necesario para descargar dependencias: Docker reutiliza esta capa si no cambian.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# Las pruebas ya corrieron antes en deploy/deploy-back.ps1 (si fallan, no se construye la imagen).
RUN ./gradlew --no-daemon bootJar -x test

# ---------- Etapa final: solo el JRE, usuario sin privilegios ----------
FROM eclipse-temurin:21.0.12_8-jre-noble

RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar

# JVM adaptada al contenedor; /tmp es el único directorio escribible (emptyDir en k8s).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

USER 10001
# 8080: API (/api) · 8081: actuator, solo para las probes de Kubernetes
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
