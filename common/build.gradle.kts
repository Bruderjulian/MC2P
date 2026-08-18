dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    api("tools.jackson.core:jackson-databind:3.2.2")
    api("org.yaml:snakeyaml:2.6")
    compileOnly("org.slf4j:slf4j-api:2.0.18")

    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.8")
}

extensions.extraProperties["moduleName"] = "common"