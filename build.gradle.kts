plugins {
    java
}

group = "de.shockbase"
version = "1.0.0"

description = "Per-player WorldBorderAPI borders that scale with player XP levels."

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "eldonexus"
        url = uri("https://eldonexus.de/repository/maven-releases/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.yannicklamprecht:worldborderapi:26.2.0.0:dev")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
