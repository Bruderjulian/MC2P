plugins {
    java
}

allprojects {
    group = "dev.mc2p"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
}