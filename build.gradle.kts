import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom") version "1.14.7"
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    `java-library`
    `maven-publish`
}

group = "com.zychen027"
version = "1.0.0"
base.archivesName.set("meteor-meteorplusplus")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
    maven("https://masa.dy.fi/maven") // Litematica 仓库

    // [新增] Baritone Maven 仓库
    maven {
        name = "impactdevelopment-repo"
        url = uri("https://impactdevelopment.github.io/maven/")
    }
}
dependencies {
    minecraft("com.mojang:minecraft:1.21.8")
    mappings("net.fabricmc:yarn:1.21.8+build.1:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.9")

    // Meteor Client
    modImplementation("meteordevelopment:meteor-client:1.21.8-SNAPSHOT")

    // Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.7+kotlin.2.2.21")
    implementation(kotlin("stdlib"))

    // [新增] Baritone API 依赖
    modImplementation("cabaletta:baritone-api:1.9")

    // [可选] Litematica 依赖 (Printer 模块需要，可选安装)
    // modImplementation("fi.dy.masa.litematica:litematica-fabric-1.21.8:0.22.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
    named<Jar>("jar") {
        archiveBaseName.set("meteor-meteorplusplus")
        archiveVersion.set(project.version.toString())
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
