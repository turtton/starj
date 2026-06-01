plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "starj"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation(platform("software.amazon.awssdk:bom:2.31.70"))
    implementation("software.amazon.awssdk:s3")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.flywaydb:flyway-mysql")
    testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:minio")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

val skipFrontend = project.hasProperty("skipFrontend")
val frontendDir = layout.projectDirectory.dir("frontend")
val frontendDist = frontendDir.dir("dist")

val installFrontend by tasks.registering(Exec::class) {
    description = "Installs frontend dependencies via Vite+ (vp install)."
    workingDir = frontendDir.asFile
    commandLine("vp", "install")
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("pnpm-workspace.yaml"))
    inputs.file(frontendDir.file("pnpm-lock.yaml"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend by tasks.registering(Exec::class) {
    description = "Builds the Preact SPA via Vite+ (vp build) into frontend/dist."
    dependsOn(installFrontend)
    workingDir = frontendDir.asFile
    commandLine("vp", "build")
    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("index.html"))
    inputs.file(frontendDir.file("vite.config.ts"))
    inputs.file(frontendDir.file("package.json"))
    outputs.dir(frontendDist)
}

tasks.named<ProcessResources>("processResources") {
    if (!skipFrontend) {
        dependsOn(buildFrontend)
        from(frontendDist) { into("static") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        if (gradle.startParameter.taskRequests.none { request -> request.args.any { it == "--tests" } }) {
            excludeTags("integration")
        }
    }
    environment("SPRING_DATASOURCE_URL", "")
    environment("SPRING_DATASOURCE_USERNAME", "")
    environment("SPRING_DATASOURCE_PASSWORD", "")
}
