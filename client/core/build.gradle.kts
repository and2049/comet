dependencies {
    compileOnly("com.google.code.gson:gson:2.2.4")
    compileOnly("org.spongepowered:mixin:${property("mixin_version")}")
    compileOnly("net.minecraft:launchwrapper:1.12") { isTransitive = false }
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
