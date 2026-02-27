import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom") version "1.14.7"
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

group = "com.zychen027"
version = "1.0.0"

// 修改 jar 名称
base.archivesName.set("meteor-meteorplusplus")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.8")
    mappings("net.fabricmc:yarn:1.21.8+build.1:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.9")
    modImplementation("meteordevelopment:meteor-client:1.21.8-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }
    
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
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
