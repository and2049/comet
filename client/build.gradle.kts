plugins {
    java
    id("xyz.wagyourtail.unimined") version "1.4.1" apply false
}

subprojects {
    apply(plugin = "java")
    group = "comet"
    version = rootProject.version

    repositories {
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://repo.spongepowered.org/maven")
        maven("https://libraries.minecraft.net")
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
    }
}
