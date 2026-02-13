import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin

object Versions {
    const val KOTLIN = "2.3.10"
}

plugins {
    id("org.springframework.boot") version "3.5.10" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("com.github.ben-manes.versions") version "0.53.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
}

group = "no.novari"
version = findProperty("version") ?: "1.0-SNAPSHOT"

extra["kotlin.version"] = Versions.KOTLIN

ktlint {
    version.set("1.8.0")
}

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

    api("no.novari:flyt-web-resource-server:2.1.0-rc-1")
    api("no.novari:flyt-kafka:5.1.0-rc-1")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
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

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return !isStable
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
