# Build from repository root: docker build -f emitter/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY capture capture/
COPY emitter emitter/
RUN mvn -q -pl emitter -DskipTests package

# jammy = multi-arch (amd64 + arm64); alpine tags often lack arm64 and break on Apple Silicon
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/emitter/target/echo-emitter-*.jar app.jar
EXPOSE 9091 8090
ENV CAPTURES_DIR=/data/captures \
    WIREMOCK_PORT=8090
VOLUME ["/data/captures"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
