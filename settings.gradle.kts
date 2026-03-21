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

// Bug 4 修复：项目名称统一为 meteor-plusplus
rootProject.name = "meteor-plusplus"
