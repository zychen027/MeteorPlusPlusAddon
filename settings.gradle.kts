pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.meteordev.org/releases")
        maven("https://maven.meteordev.org/snapshots")
    }
}

rootProject.name = "meteor-packetmine"
