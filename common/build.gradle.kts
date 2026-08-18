plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    api("tools.jackson.core:jackson-databind:3.0.3")
    api("org.yaml:snakeyaml:2.6")
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.6")
}

extensions.extraProperties["moduleName"] = "common"