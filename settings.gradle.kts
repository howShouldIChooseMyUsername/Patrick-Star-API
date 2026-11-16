pluginManagement {
    repositories {
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.fabric-amper.settings") {
                useModule("com.github.howShouldIChooseMyUsername:Fabric-Amper:v0.1.3")
            }
        }
    }
}

plugins {
    id("dev.fabric-amper.settings") version "0.1.3"
}

rootProject.name = "patrick-star-api"
