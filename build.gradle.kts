import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

group = "no.fintlabs"
version = findProperty("version") ?: "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.fintlabs.no/releases")
    }
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

val apiVersion: String by project

dependencies {

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-core:6.0.0")

    api("org.apache.httpcomponents.client5:httpclient5:5.4.2")

    implementation("no.fint:fint-arkiv-resource-model-java:$apiVersion")

    implementation("no.fintlabs:fint-flyt-web-resource-server:1.0.0-rc-1")

    implementation("no.fintlabs:fint-flyt-cache:1.2.3")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("no.fintlabs:fint-kafka:4.0.1")
    implementation("no.fintlabs:fint-flyt-kafka:3.1.1")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

java {
    withSourcesJar()
}

ktlint {
    version.set("1.7.1")
    ignoreFailures.set(false)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

apply(from = "https://raw.githubusercontent.com/FINTLabs/fint-buildscripts/master/reposilite.ga.gradle")
