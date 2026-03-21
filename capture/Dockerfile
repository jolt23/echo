# Build from repository root: docker build -f capture/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY capture capture/
COPY emitter emitter/
RUN mvn -q -pl capture -DskipTests package

# jammy = multi-arch (amd64 + arm64); alpine tags often lack arm64 and break on Apple Silicon
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/capture/target/echo-capture-*.jar app.jar
EXPOSE 9090
ENV CAPTURES_DIR=/data/captures \
    BACKEND_BASE_URL=http://host.docker.internal:8080
VOLUME ["/data/captures"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
