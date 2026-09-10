pluginManagement {
    repositories {
        maven("https://maven.wagyourtail.xyz/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
rootProject.name = "comet-client"
include("core", "mc-1.8.9", "mc-1.7.10")
