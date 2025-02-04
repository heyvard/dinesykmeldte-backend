import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.7.3"
val jacksonVersion = "2.15.2"
val kluentVersion = "1.73"
val logbackVersion = "1.4.11"
val ktorVersion = "2.3.3"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val smCommonVersion = "1.0.1"
val mockkVersion = "1.13.7"
val nimbusdsVersion = "9.31"
val hikariVersion = "5.0.1"
val flywayVersion = "9.21.1"
val postgresVersion = "42.6.0"
val testContainerVersion = "1.18.3"
val kotlinVersion = "1.9.0"
val sykepengesoknadKafkaVersion = "2022.10.28-10.24-aa0eced7"
val swaggerUiVersion = "5.3.1"
val kotestVersion = "5.6.2"
val googlePostgresVersion = "1.13.1"
val googleOauthVersion = "1.34.1"
val ktfmtVersion = "0.44"

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
}

plugins {
    id("com.diffplug.spotless") version "6.20.0"
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
    id("org.cyclonedx.bom") version "1.7.4"
}

val githubUser: String by project
val githubPassword: String by project



repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
        implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
        implementation("io.prometheus:simpleclient_common:$prometheusVersion")

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-cors:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

        implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
        implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("com.zaxxer:HikariCP:$hikariVersion")
        implementation("org.flywaydb:flyway-core:$flywayVersion")
        implementation("org.postgresql:postgresql:$postgresVersion")
        implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")
        implementation("com.google.oauth-client:google-oauth-client:$googleOauthVersion")

        swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")

        testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.testcontainers:postgresql:$testContainerVersion")
        testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
            exclude(group = "org.eclipse.jetty")
        }
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-property:$kotestVersion")
    }

    swaggerSources {
        create("dinesykmeldte-backend").apply {
            setInputFile(file("api/oas3/dinesykmeldte-backend-api.yaml"))
        }
    }

    tasks {
        withType<Jar> {
            manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
        }
        create("printVersion") {
            println(project.version)
        }

        withType<org.hidetake.gradle.swagger.generator.GenerateSwaggerUI> {
            outputDir = File(buildDir.path + "/resources/main/api")
            dependsOn("jar")
        }

        withType<ShadowJar> {
            transform(ServiceFileTransformer::class.java) {
                setPath("META-INF/cxf")
                include("bus-extensions.txt")
            }
            dependsOn("generateSwaggerUI")
        }

        withType<Test> {
            useJUnitPlatform {
            }
            testLogging {
                events("skipped", "failed")
                showStackTraces = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

        spotless {
            kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
            check {
                dependsOn("spotlessApply")
            }
        }
    }

}

