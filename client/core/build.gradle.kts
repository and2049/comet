dependencies {
    compileOnly("com.google.code.gson:gson:2.2.4")
    compileOnly("org.spongepowered:mixin:${property("mixin_version")}")
    compileOnly("net.minecraft:launchwrapper:1.12") { isTransitive = false }
}
