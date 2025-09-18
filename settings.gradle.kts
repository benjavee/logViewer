pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Choisissez un seul nom pour votre projet
rootProject.name = "logViewer"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
