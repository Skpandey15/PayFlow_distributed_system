# Lab-only test double for the external settlement rails. Build: ./gradlew :rail-simulator:jar
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10002 railsim && adduser -S -D -H -u 10002 -G railsim railsim
COPY build/libs/rail-simulator.jar /app/rail-simulator.jar
USER 10002:10002
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx128m", "-jar", "/app/rail-simulator.jar"]
