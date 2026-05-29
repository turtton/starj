# starj

Spring Boot 4 + Kotlin greenfield app. Single Gradle module, minimal source today.

## Setup

Requires **JDK 17**. Use the Nix dev shell (preferred):

```bash
direnv allow   # loads flake via .envrc
# or: nix develop
```

The dev shell provides `jdk17` plus the frontend toolchain: `vp` (Vite+, via the `nix-vite-plus` flake input), `nodejs_24`, and `pnpm`. Nothing needs to be installed globally.

Without direnv, ensure `java -version` reports 17 before running Gradle.

## Commands

```bash
./gradlew build                              # compile + test
./gradlew test                               # all tests (JUnit 5)
./gradlew test --tests "net.turtton.starj.StarjApplicationTests"
./gradlew bootRun                            # run app (default port 8080)
./gradlew bootJar                            # executable jar
```

Local infrastructure uses **Podman Compose** (not Docker Compose):

```bash
cp .env.example .env
direnv allow                                   # loads .env via .envrc
podman compose up -d mysql                   # MySQL on localhost:3306
./gradlew bootRun                              # reads SPRING_DATASOURCE_* from env
```

No dedicated lint/format/typecheck tasks — `./gradlew build` is the verification gate.

Gradle wrapper: **9.4.1**. Kotlin **2.2.21**, Spring Boot **4.0.6**.

## Frontend (`frontend/`)

Preact SPA built with **Vite+** (`vp`), routed with **preact-iso**, state via **@preact/signals**, styled with **Tailwind CSS v4**. Served same-origin: the dev server proxies `/api` to the backend; the production build is bundled into the Spring Boot jar.

```bash
cd frontend
vp install                                    # install JS deps (pnpm under the hood)
vp dev                                        # dev server on :5173, proxies /api -> :8080
vp check                                      # format, lint, type-check
vp build                                      # production build -> frontend/dist
```

`vp` comes from the Nix dev shell (no global install). On first use, run `vp env off` once so Vite+ reuses the Nix-managed Node/pnpm instead of downloading its own runtime.

Auth is session/cookie based with CSRF: the app primes the `XSRF-TOKEN` cookie via `GET /api/auth/csrf`, then echoes it in the `X-XSRF-TOKEN` header on mutating requests (the backend uses the plain `CsrfTokenRequestAttributeHandler` so the raw cookie value is accepted).

**Production bundling:** `./gradlew bootJar` (or `bootRun`) runs `buildFrontend` (`vp build`) and copies `frontend/dist` into the jar's `static/`, so Spring serves the SPA at `/`. Client deep links (`/login`, `/register`) are forwarded to `index.html` by `SpaForwardingController`. Skip the frontend build with `-PskipFrontend` (used for backend-only CI without `vp`):

```bash
./gradlew build -PskipFrontend                # backend only, no vp required
```

Run Gradle from inside the dev shell so `vp` is on the Gradle daemon's `PATH` (direnv loads it automatically in the project dir). If a daemon was first started outside the shell you may see `A problem occurred starting process 'command 'vp''` — run `./gradlew --stop` and retry inside the shell.

## Layout

| Path | Role |
|------|------|
| `src/main/kotlin/net/turtton/starj/` | Application code (`StarjApplication.kt` entrypoint) |
| `src/main/resources/` | Config, Flyway migrations (`db/migration/`), MyBatis XML mappers |
| `src/test/kotlin/net/turtton/starj/` | Tests (`@SpringBootTest`) |

Package namespace: **`net.turtton.starj`**. `build.gradle.kts` still has `group = "com.example"` from Initializr — align when adding publishing.

## Stack (on classpath, mostly unwired)

Dependencies declare intent; almost none is implemented yet:

- **WebMVC** — REST controllers go under `net.turtton.starj`
- **Spring Security** — active with default deny-all; add `SecurityFilterChain` config or tests need `@AutoConfigureMockMvc(addFilters = false)` / test security setup
- **MyBatis** — mappers as interfaces + XML under `src/main/resources/mapper/`; no starter config yet
- **Flyway** — SQL migrations in `src/main/resources/db/migration/`; requires a configured datasource
- **Spring Batch** — job definitions not present yet
- **SpringDoc OpenAPI** — UI typically at `/swagger-ui.html` once controllers exist

## Database (critical for tests and runtime)

**Runtime (local):** Datasource is configured via environment variables loaded from `.env` by direnv (`.envrc` → `dotenv`):

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL (e.g. `jdbc:mysql://localhost:3306/starj`) |
| `SPRING_DATASOURCE_USERNAME` | DB user (shared with compose `MYSQL_USER`) |
| `SPRING_DATASOURCE_PASSWORD` | DB password (shared with compose `MYSQL_PASSWORD`) |

`runtimeOnly("com.mysql:mysql-connector-j")` is on the classpath. `application.properties` only sets `spring.application.name=starj`; datasource values come from env.

**Tests:** H2 in-memory via `src/test/resources/application.properties` (`spring.flyway.enabled=false`).

## Conventions

- Kotlin compiler flags: `-Xjsr305=strict`, `-Xannotation-default-target=param-property`
- Tests use JUnit 5 (`useJUnitPlatform()`)
- No CI workflows, pre-commit hooks, or formatter config in repo yet
- `HELP.md` is Initializr boilerplate — link list only, not project docs

## Adding features (typical order)

1. Datasource + Flyway migration
2. MyBatis mapper/repository layer
3. Service + WebMVC controller
4. Security rules (permit API docs, protect business endpoints)
5. Batch jobs (if needed)

Keep new code under `net.turtton.starj` and match Spring Boot 4 / Kotlin idioms already in `build.gradle.kts`.
