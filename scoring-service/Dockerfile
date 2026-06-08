FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y maven curl && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY sentinel-commons/ sentinel-commons/
COPY ingestion-service/ ingestion-service/
COPY rules-engine-service/ rules-engine-service/
COPY scoring-service/ scoring-service/
COPY case-management-service/ case-management-service/
COPY feedback-service/ feedback-service/
COPY api-gateway/ api-gateway/

RUN mvn package -pl scoring-service -am -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r sentinel && useradd -r -g sentinel sentinel

COPY --from=builder /build/scoring-service/target/sentinel-fraud-detection.jar app.jar

RUN chown sentinel:sentinel app.jar

USER sentinel

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]