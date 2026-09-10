dependencies {
    compileOnly("com.google.code.gson:gson:2.2.4")
    compileOnly("org.spongepowered:mixin:${property("mixin_version")}")
    compileOnly("net.minecraft:launchwrapper:1.12") { isTransitive = false }
    compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.1") { isTransitive = false }
    compileOnly("net.java.jinput:jinput:2.0.5")
}

configurations.testCompileOnly {
    extendsFrom(configurations.compileOnly.get())
}

val regression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + configurations.compileClasspath.get()
    mainClass.set("comet.core.mod.CoreTests")
    systemProperty("java.awt.headless", "true")
    providers.gradleProperty("previewDir").orNull?.let { systemProperty("comet.previewDir", it) }
}

tasks.check {
    dependsOn(regression)
}

tasks.register<JavaExec>("blurGpuRegression") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + configurations.compileClasspath.get()
    mainClass.set("comet.core.render.BlurGpuTests")
    providers.gradleProperty("nativeDir").orNull?.let { systemProperty("org.lwjgl.librarypath", it) }
}
