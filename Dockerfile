# syntax=docker/dockerfile:1.7
# PayFlow WP-01 runtime image.
#
# Build once, test it, ship that artifact: the image packages the jar produced (and tested) by
#   ./gradlew build
# instead of recompiling inside Docker. Recompiling here would ship bytes that were never tested, and the
# Testcontainers-based test suite cannot run inside a plain docker build anyway. Jenkins (a later WP) runs
# `./gradlew build` and then `docker build`.

FROM eclipse-temurin:25-jre-alpine AS layers
WORKDIR /workspace
COPY build/libs/payflow.jar payflow.jar
RUN java -Djarmode=tools -jar payflow.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine
# Non-root, fixed UID (Kubernetes runAsNonRoot / runAsUser friendly).
RUN addgroup -S -g 10001 payflow && adduser -S -D -H -u 10001 -G payflow payflow
WORKDIR /app
# Layered copy: dependencies change rarely, application classes often, so pulls of new versions stay small.
COPY --from=layers --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=layers --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=10001:10001 /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
# Container-aware heap sizing; crash fast on OOM so the orchestrator restarts a clean JVM.
# G1 explicitly (the ergonomic default below 1792 MiB is SerialGC; measured in WP-03 TUNING-RESULTS: 38.6 s pause
# under stress with Serial vs 71 ms with G1). 70 % heap leaves room for metaspace, threads and direct buffers.
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
