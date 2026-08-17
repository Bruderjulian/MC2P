plugins {
    application
}

dependencies {
    implementation(project(":common"))
    implementation("org.yaml:snakeyaml:2.3")
    compileOnly("org.slf4j:slf4j-api:2.0.13")
}

application {
    mainClass.set("dev.mc2p.deploy.DeployCli")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.mc2p.deploy.DeployCli"
    }
}