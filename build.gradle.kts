plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.dataforge"
version = "0.0.1-SNAPSHOT"
description = "ReserveFlow"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JPA and Hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Database migrations
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // Request validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // REST API
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // PostgreSQL migrations
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC driver
    runtimeOnly("org.postgresql:postgresql")

    // JPA tests
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")

    // Flyway tests
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")

    // Validation tests
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")

    // MVC tests
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")

    // JUnit launcher
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
