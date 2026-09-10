plugins {
    id("xyz.wagyourtail.unimined")
}

val bundle: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(bundle)

unimined.minecraft(sourceSets.main.get()) {
    version("1.7.10")
    side("client")
    mappings {
        searge()
        mcp("stable", "12-1.7.10")
    }
    defaultRemapJar = true
    runs {
        config("client") {
            mainClass.set("net.minecraft.launchwrapper.Launch")
            args("--tweakClass", "comet.launch.CometTweaker")
        }
    }
}

dependencies {
    bundle(project(":core"))
    bundle("org.spongepowered:mixin:${property("mixin_version")}")
    bundle("org.ow2.asm:asm:${property("asm_version")}")
    bundle("org.ow2.asm:asm-tree:${property("asm_version")}")
    bundle("org.ow2.asm:asm-commons:${property("asm_version")}")
    bundle("org.ow2.asm:asm-util:${property("asm_version")}")
    bundle("org.ow2.asm:asm-analysis:${property("asm_version")}")
    bundle("com.google.guava:guava:17.0")
    compileOnly("net.minecraft:launchwrapper:1.12") { isTransitive = false }
    runtimeOnly("net.minecraft:launchwrapper:1.12") { isTransitive = false }
}

tasks.jar {
    manifest.attributes("TweakClass" to "comet.launch.CometTweaker")
}

val dist by tasks.registering(Jar::class) {
    dependsOn("remapJar", bundle)
    archiveFileName.set("comet-client-1.7.10.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("dist"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes("TweakClass" to "comet.launch.CometTweaker")
    from(tasks.named("remapJar").map { task -> task.outputs.files.map { zipTree(it) } })
    from(provider { bundle.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/**")
}

tasks.build {
    dependsOn(dist)
}
