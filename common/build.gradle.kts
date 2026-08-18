plugins {
    `java-library`
}

dependencies {
    api("tools.jackson.core:jackson-databind:3.0.3")
    api("org.yaml:snakeyaml:2.3")
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.6")
}