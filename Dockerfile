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
COPY --from=build /app/build/libs/app.jar /tmp/app.jar

# CDS (Class Data Sharing): en Cloud Run la app arranca desde cero cada vez que estuvo inactiva.
# Se arranca una vez aquí hasta cargar el contexto de Spring (spring.context.exit=onRefresh) y la JVM
# guarda las clases ya procesadas en application.jsa; el arranque real las lee de ese archivo.
# Esta corrida NO se conecta a la base: Flyway apagado y Hibernate sin leer metadatos (por eso el
# dialecto explícito). Los valores "cds" solo existen en esta corrida.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/application \
 && rm /tmp/app.jar \
 && SPRING_PROFILES_ACTIVE=cloudrun FIREBASE_PROJECT_ID=cds DB_URL=jdbc:mysql://127.0.0.1:1/cds DB_USERNAME=cds \
    java -XX:ArchiveClassesAtExit=/app/application.jsa -Dspring.context.exit=onRefresh \
         -jar /app/application/app.jar \
         --spring.datasource.password=cds \
         --spring.flyway.enabled=false \
         --spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect \
         --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false

# JVM adaptada al contenedor; /tmp es el único directorio que la app necesita escribir.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

USER 10001
# Cloud Run envía el tráfico al puerto de la variable PORT (8080 por defecto).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application.jsa", "-jar", "/app/application/app.jar"]
