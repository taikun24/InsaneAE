pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // versions/<MC>-<ローダー> ごとに、ローダー専用のビルドスクリプトを割り当てる。
        // 比較に使うバージョン (コメントの `>=1.21` など) は MC のバージョン。
        version("1.20.1-forge", "1.20.1").buildscript("build.forge.gradle")
        version("1.21.1-neoforge", "1.21.1").buildscript("build.neoforge.gradle")
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "insaneae"
