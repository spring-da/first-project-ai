FROM maven:3.9.16-eclipse-temurin-26-noble AS builder

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline

COPY src ./src

RUN mvn -B --no-transfer-progress clean package \
    && find target -maxdepth 1 -type f \
       -name "*.jar" ! -name "*.original" \
       -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:26-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring \
       --home-dir /app \
       --shell /usr/sbin/nologin spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]