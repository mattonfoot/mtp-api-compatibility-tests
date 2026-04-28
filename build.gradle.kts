plugins {
    kotlin("jvm") version "2.3.10"
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.rest-assured:rest-assured:5.5.1")
    testImplementation("io.rest-assured:kotlin-extensions:5.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.4")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.4")
    testImplementation("org.postgresql:postgresql:42.7.5")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
    testImplementation("ch.qos.logback:logback-classic:1.5.18")
}

tasks.test {
    useJUnitPlatform {
        val tags = System.getenv("TEST_TAGS")
        if (!tags.isNullOrBlank()) {
            includeTags(tags)
        }
    }
    systemProperty("api.target", System.getenv("API_TARGET") ?: "kotlin")
    systemProperty("api.base-url", System.getenv("API_BASE_URL") ?: "http://localhost:8080")
    systemProperty("db.url", System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/mtp_api")
    systemProperty("db.user", System.getenv("DB_USER") ?: "postgres")
    systemProperty("db.password", System.getenv("DB_PASS") ?: "postgres")
    systemProperty("hmpps.auth.url", System.getenv("HMPPS_AUTH_URL") ?: "http://localhost:8090/auth")
    systemProperty("hmpps.auth.client-id", System.getenv("HMPPS_AUTH_CLIENT_ID") ?: "community-api-client")
    systemProperty("hmpps.auth.client-secret", System.getenv("HMPPS_AUTH_CLIENT_SECRET") ?: "community-api-client")
}
