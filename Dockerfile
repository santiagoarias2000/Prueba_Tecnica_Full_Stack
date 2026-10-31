# syntax=docker/dockerfile:1
# Imagen multi-stage de polizas-api (Java 17).

# ---------- Etapa 1: build ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Primero el wrapper y el pom para cachear las dependencias entre builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B -DskipTests package \
    && cp target/polizas-api-*.jar /workspace/app.jar

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    POLIZAS_API_KEY=123456

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
