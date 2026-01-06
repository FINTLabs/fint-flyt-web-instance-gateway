pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.fintlabs.no/releases")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "flyt-web-instance-gateway"
