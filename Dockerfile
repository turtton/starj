FROM node:24-bookworm-slim AS build

ENV PNPM_HOME=/pnpm
ENV PATH=${PNPM_HOME}:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

RUN corepack enable \
    && corepack prepare pnpm@11.4.0 --activate \
    && pnpm config set global-bin-dir "${PNPM_HOME}" \
    && pnpm add --global vite-plus@0.1.23 \
    && vp --version

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY frontend/package.json frontend/pnpm-workspace.yaml frontend/pnpm-lock.yaml ./frontend/

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon help

RUN ./gradlew --no-daemon installFrontend

COPY src ./src
COPY frontend ./frontend

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-noble AS runtime

WORKDIR /app

RUN groupadd --system starj \
    && useradd --system --gid starj --home-dir /app --shell /usr/sbin/nologin starj

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER starj

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
