plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    api("tools.jackson.core:jackson-databind:3.0.3")
    api("org.yaml:snakeyaml:2.3")
    compileOnly("org.slf4j:slf4j-api:2.0.13")
}

extensions.extraProperties["moduleName"] = "common"