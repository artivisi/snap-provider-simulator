# syntax=docker/dockerfile:1.7
# SNAP Provider Simulator: Maven build, then a layered Spring Boot runtime on Alpine.
# PostgreSQL is external (see compose.yml).

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
# .git is read by git-commit-id-maven-plugin (build info).
COPY .git ./.git
COPY lombok.config .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package && \
    mkdir -p /out && cp target/snap-provider-simulator-*.jar /out/app.jar && \
    cd /out && java -Djarmode=tools -jar app.jar extract --layers --destination layers

FROM azul/zulu-openjdk-alpine:25-jre
LABEL org.opencontainers.image.title="SNAP Provider Simulator" \
      org.opencontainers.image.description="Simulates the publicly documented SNAP VA behaviour of BRI and BCA for partner app testing; not affiliated with or endorsed by either bank" \
      org.opencontainers.image.source="https://github.com/artivisi/snap-provider-simulator" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="PT Artivisi Intermedia"

RUN apk add --no-cache tzdata tini wget && \
    cp /usr/share/zoneinfo/Asia/Jakarta /etc/localtime && \
    echo "Asia/Jakarta" > /etc/timezone && \
    addgroup -S app && adduser -S app -G app

WORKDIR /opt/app
COPY --from=build --chown=app:app /out/layers/dependencies/ ./
COPY --from=build --chown=app:app /out/layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /out/layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /out/layers/application/ ./

USER app
# server.port in application.yml; the healthcheck probes it.
EXPOSE 9090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# extract --layers emits a runnable app.jar plus lib/; launch the jar directly.
ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /opt/app/app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/liveness || exit 1
