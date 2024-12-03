plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "net.labymod.addons.minimap"
version = "1.0.0"

labyMod {
    defaultPackageName = "net.labymod.addons.minimap"

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    // When the property is set to true, you can log in with a Minecraft account
                    // devLogin = true
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
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version
}
