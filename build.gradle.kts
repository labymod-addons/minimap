import net.labymod.labygradle.common.extension.model.labymod.ReleaseChannel

plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val unsupportedVersions = arrayOf("1.8.9", "1.12.2", "1.16.5")
val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "net.labymod.addons.minimap"
version = "1.0.0"

labyMod {
    defaultPackageName = "net.labymod.addons.minimap"

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    devLogin = true
                }

                create("clientRenderdoc") {
                    parent = findByName("client")
                    devLogin = true
                    enabled = !unsupportedVersions.contains(versionId)
                    ideaConfiguration = providers.environmentVariable("RENDERDOC").isPresent
                    jvmArgs("-Dnet.labymod.debugging.renderdoc=true")
                }
            }
        }
    }

    addonInfo {
        namespace = "labysminimap"
        displayName = "Laby's Minimap"
        author = "LabyMod"
        description = "Minimap shows you a small map with an overview of the surrounded world."
        minecraftVersion = "*"
        version = rootProject.version.toString()
        addon("labyswaypoints")
        releaseChannel = ReleaseChannel.create("internal_4.3")
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenLocal()
    }
}
