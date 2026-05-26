# starj

Spring Boot 4 + Kotlin greenfield app. Single Gradle module, minimal source today.

## Setup

Requires **JDK 17**. Use the Nix dev shell (preferred):

```bash
direnv allow   # loads flake via .envrc
# or: nix develop
```

Without direnv, ensure `java -version` reports 17 before running Gradle.

## Commands

```bash
./gradlew build                              # compile + test
./gradlew test                               # all tests (JUnit 5)
./gradlew test --tests "net.turtton.starj.StarjApplicationTests"
./gradlew bootRun                            # run app (default port 8080)
./gradlew bootJar                            # executable jar
```

No dedicated lint/format/typecheck tasks — `./gradlew build` is the verification gate.

Gradle wrapper: **9.4.1**. Kotlin **2.2.21**, Spring Boot **4.0.6**.

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

`application.properties` only sets `spring.application.name=starj`. **No JDBC driver or URL is configured**, yet Flyway/MyBatis autoconfiguration expects a datasource.

`./gradlew test` currently fails with:

```
Failed to determine a suitable driver class
```

Before `@SpringBootTest` can pass, add e.g. H2 for tests:

- `testImplementation("com.h2database:h2")` + `src/test/resources/application.properties`, or
- `@SpringBootTest` with `@AutoConfigureTestDatabase` / `@MockBean DataSource`, or
- exclude JDBC/Flyway autoconfig in tests until DB work begins

Pick one approach and document it here when implemented.

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
