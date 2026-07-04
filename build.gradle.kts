plugins {
    java
}

group = "de.shockbase"
version = "1.0.0"

description = "Per-player WorldBorderAPI borders that scale with player XP levels."

val worldBorderApiVersion = "26.2.0.0"
val worldBorderApiClassifier = "dev"
val targetMinecraftApiVersion = worldBorderApiVersion.split(".").take(2).joinToString(".")
val paperApiVersion = "$targetMinecraftApiVersion.build.+"
val javaTargetVersionByWorldBorderMajor = mapOf(
    "26" to 25,
)
val javaTargetVersion = javaTargetVersionByWorldBorderMajor[targetMinecraftApiVersion.substringBefore(".")]
    ?: error("No Java target configured for WorldBorderAPI $worldBorderApiVersion.")

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
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.github.yannicklamprecht:worldborderapi:$worldBorderApiVersion:$worldBorderApiClassifier")
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
    description = "Ensures Paper API target is derived from the WorldBorderAPI target."

    doLast {
        check(paperApiVersion == "$targetMinecraftApiVersion.build.+") {
            "Paper API version must be derived from WorldBorderAPI version $worldBorderApiVersion."
        }
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
            |- Paper API: `$paperApiVersion` (derived from WorldBorderAPI)
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
