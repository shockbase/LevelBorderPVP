plugins {
    java
}

group = "de.shockbase"
val worldBorderApiVersion = "26.2.0.0"
val worldBorderApiClassifier = "dev"
version = worldBorderApiVersion

description = "Per-player WorldBorderAPI borders that scale with player XP levels."

val worldBorderApiVersionParts = worldBorderApiVersion.split(".")
require(worldBorderApiVersionParts.size >= 3) {
    "WorldBorderAPI version $worldBorderApiVersion does not contain a Minecraft/Paper target."
}
val targetMinecraftApiVersion = worldBorderApiVersionParts
    .take(3)
    .dropLastWhile { it == "0" }
    .joinToString(".")
val paperApiVersion = "$targetMinecraftApiVersion.build.+"
val javaTargetVersionByWorldBorderMajor = mapOf(
    "26" to 25,
)
val javaTargetVersion = javaTargetVersionByWorldBorderMajor[targetMinecraftApiVersion.substringBefore(".")]
    ?: error("No Java target configured for WorldBorderAPI $worldBorderApiVersion.")

repositories {
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven {
                name = "papermc"
                url = uri("https://repo.papermc.io/repository/maven-public/")
            }
        }
        filter {
            includeGroup("io.papermc.paper")
            includeGroup("com.mojang")
            includeGroup("net.md-5")
        }
    }

    exclusiveContent {
        forRepository {
            maven {
                name = "eldonexus"
                url = uri("https://eldonexus.de/repository/maven-releases/")
            }
        }
        filter {
            includeGroup("com.github.yannicklamprecht")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.github.yannicklamprecht:worldborderapi:$worldBorderApiVersion:$worldBorderApiClassifier")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("com.github.yannicklamprecht:worldborderapi:$worldBorderApiVersion:$worldBorderApiClassifier")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

configurations.configureEach {
    resolutionStrategy.componentSelection {
        all {
            if (
                candidate.group == "io.papermc.paper" &&
                candidate.module == "paper-api" &&
                !candidate.version.endsWith("-stable")
            ) {
                reject("Only stable Paper API builds are allowed.")
            }
        }
    }
}

val resolvedPaperApiVersion = providers.provider {
    configurations.named("compileClasspath").get()
        .incoming.resolutionResult.allComponents
        .mapNotNull { it.moduleVersion }
        .single { it.group == "io.papermc.paper" && it.name == "paper-api" }
        .version
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaTargetVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version,
            "apiVersion" to targetMinecraftApiVersion,
        )
    }
}

tasks.register("validateWorldBorderApiTarget") {
    group = "verification"
    description = "Ensures the stable Paper API target is derived from the WorldBorderAPI target."

    doLast {
        check(paperApiVersion == "$targetMinecraftApiVersion.build.+") {
            "Paper API version must be derived from WorldBorderAPI version $worldBorderApiVersion."
        }
        check(
            resolvedPaperApiVersion.get().startsWith("$targetMinecraftApiVersion.build.") &&
                resolvedPaperApiVersion.get().endsWith("-stable")
        ) {
            "Resolved Paper API ${resolvedPaperApiVersion.get()} does not match " +
                "WorldBorderAPI $worldBorderApiVersion or is not stable."
        }
    }
}

tasks.register("printPaperApiVersion") {
    group = "help"
    description = "Prints the resolved stable Paper API version."

    doLast {
        println(resolvedPaperApiVersion.get())
    }
}

tasks.named("check") {
    dependsOn("validateWorldBorderApiTarget")
}

tasks.register("syncReadmeVersionTarget") {
    group = "documentation"
    description = "Updates README and Java version targets from Gradle metadata."

    doLast {
        val readmeFile = layout.projectDirectory.file("README.md").asFile
        val javaVersionFile = layout.projectDirectory.file(".java-version").asFile

        val section = """
            |## Version target
            |
            |- Plugin version: `${project.version}`
            |- WorldBorderAPI: `$worldBorderApiVersion:$worldBorderApiClassifier`
            |- Minecraft/Paper API version: `$targetMinecraftApiVersion`
            |- Paper API: `$paperApiVersion` (latest stable build derived from WorldBorderAPI)
            |- Java toolchain: `$javaTargetVersion` (derived from WorldBorderAPI)
            |
            |WorldBorderAPI must also be installed as a server plugin. This plugin only compiles against the API and declares `depend: [WorldBorderAPI]`.
        """.trimMargin()

        val original = readmeFile.readText()
        val updated = Regex("""(?s)## Version target\R.*?(?=\R## |\z)""").replace(original, "$section\n")

        if (updated != original) {
            readmeFile.writeText(updated)
        }

        val javaVersionText = "$javaTargetVersion\n"
        if (!javaVersionFile.exists() || javaVersionFile.readText() != javaVersionText) {
            javaVersionFile.writeText(javaVersionText)
        }
    }
}
