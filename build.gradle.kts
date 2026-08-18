import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("com.diffplug.spotless") version "8.9.0" apply false
}

allprojects {
    group = "dev.mc2p"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    
    dependencies {
        testImplementation(platform("org.junit:junit-bom:6.1.3"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat("2.50.0")
            target("src/*/java/**/*.java")
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        maxErrors = 0
        maxWarnings = 0
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(false)
            html.required.set(false)
        }
    }
}