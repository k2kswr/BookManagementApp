import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.7.2")
        classpath("org.postgresql:postgresql:42.7.8")
    }
}

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "11.7.2"
    id("org.jooq.jooq-codegen-gradle") version "3.19.35"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
kotlin { jvmToolchain(21) }
extra["kotlin.version"] = "2.1.21"

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    jooqCodegen("org.postgresql:postgresql:42.7.8")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val databaseUrl = providers.environmentVariable("DB_URL").getOrElse("jdbc:postgresql://localhost:5432/books")
val databaseUser = providers.environmentVariable("DB_USER").getOrElse("books")
val databasePassword = providers.environmentVariable("DB_PASSWORD").getOrElse("books")

flyway {
    url = databaseUrl
    user = databaseUser
    password = databasePassword
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    cleanDisabled = true
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = databaseUrl
            user = databaseUser
            password = databasePassword
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                includes = "authors|books|book_authors"
            }
            target {
                packageName = "com.example.bookmanagement.jooq"
                directory = "build/generated/jooq"
            }
        }
    }
}

sourceSets.main { java.srcDir("build/generated/jooq") }
tasks.named("jooqCodegen") { dependsOn("flywayMigrate") }
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
}
tasks.named("compileKotlin") { dependsOn("jooqCodegen") }
tasks.named("compileJava") { dependsOn("jooqCodegen") }
tasks.matching { it.name.startsWith("runKtlint") && it.name.endsWith("MainSourceSet") }.configureEach {
    mustRunAfter("jooqCodegen")
}
tasks.test { useJUnitPlatform { excludeTags("integration") } }

val integrationTest by tasks.registering(Test::class) {
    description = "Runs HTTP and database integration tests against PostgreSQL."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(integrationTest) }
ktlint {
    version.set("1.5.0")
    filter { exclude("**/generated/**") }
}
