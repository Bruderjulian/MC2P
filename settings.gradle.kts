pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
}

plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mc2p"

include("common")
include("plugin")
include("proxy")


dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Paper
        maven {
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            url = uri("https://repo.maven.apache.org/maven2/")
        }
        maven {
            url = uri("https://jitpack.io") 
        }
        maven {
            url = uri("https://hub.spigotmc.org/nexus/content/groups/public/")
        }
        maven {
            url = uri("http://nexus.hc.to/content/repositories/pub_releases")
            isAllowInsecureProtocol = true
        }

        maven {
            url = uri("https://repo.codemc.org/repository/maven-public")
        }

        // PlaceholderAPI
        maven {
            url = uri("https://repo.extendedclip.com/releases/")
        }
    }
}