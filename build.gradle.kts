import org.gradle.authentication.http.BasicAuthentication
import org.springframework.boot.gradle.plugin.SpringBootPlugin

object Versions {
    const val KOTLIN = "2.2.21"
}

plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.53.0"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
}

group = "no.novari"
version = findProperty("version") ?: "1.0-SNAPSHOT"

extra["kotlin.version"] = Versions.KOTLIN

private val fintLabsRepo = uri("https://repo.fintlabs.no/releases")

repositories {
    mavenLocal()
    maven {
        url = fintLabsRepo
    }
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-oauth2-client")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
    implementation("org.apache.httpcomponents.client5:httpclient5")

    api("no.novari:flyt-web-resource-server:2.0.0")
    api("no.novari:flyt-kafka:4.0.0")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.named<Jar>("jar") {
    enabled = true
}

java {
    withSourcesJar()
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

publishing {
    repositories {
        maven {
            url = fintLabsRepo
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
