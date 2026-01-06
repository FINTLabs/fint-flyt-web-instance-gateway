val fintLabsRepo = uri("https://repo.fintlabs.no/releases")

pluginManagement {
    repositories {
        maven {
            url = fintLabsRepo
        }
        gradlePluginPortal()
    }
}

rootProject.name = "flyt-web-instance-gateway"
