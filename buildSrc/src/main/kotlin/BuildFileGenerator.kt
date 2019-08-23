import java.io.File

fun generateBuildFile(
    projectVersion: String,
    cdkVersion: Version,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    baseDir: File
) {
    val targetDir = File(baseDir, cdkVersion.version)
    targetDir.mkdirs()
    File(targetDir, "build.gradle.kts").apply {
        createNewFile()
        writeText(getBuildFileText(projectVersion, cdkVersion, kotlinVersion, bintrayUser, bintrayApiKey, targetDir))
    }
    File(targetDir, "settings.gradle").apply {
        createNewFile()
        writeText(settingsGradleFileText)
    }
    val pb = ProcessBuilder("gradle", "run", "clean", "bintrayUpload")
    pb.inheritIO()
    pb.directory(targetDir)
    pb.environment()["PATH"] = System.getenv("PATH")
    pb.start().waitFor()
}

private fun getBuildFileText(
    projectVersion: String,
    cdkVersion: Version,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    targetDir: File
): String = """
import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "jp.justincase"
version = "$cdkVersion-${projectVersion.split('-')[1]}"

repositories {
    mavenCentral()
    mavenLocal()
}

plugins {
    kotlin("jvm") version "$kotlinVersion"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
    id("application")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    // generator
    runtime("jp.justincase:cdk-dsl-generator:$projectVersion")
    // AWS CDK
    implementation("software.amazon.awscdk", "lambda", "$cdkVersion")
    implementation("software.amazon.awscdk", "logs-destinations", "$cdkVersion")
}

application {
    mainClassName = "jp.justincase.cdkdsl.generator.MainKt"
}

publishing {
    publications {
        register("maven", MavenPublication::class.java) {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
        }
    }
}

bintray {
    key = "$bintrayApiKey"
    user = "$bintrayUser"
    setPublications("maven")
    publish = true
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        userOrg = "justincase"
            repo = "Maven"
            name = "aws-cdk-kotlin-dsl"
            version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = project.version as String
        })
    }) 
}

tasks.withType<JavaExec> {
    args("${targetDir.absolutePath.replace("\\", "\\\\")}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
"""

private val settingsGradleFileText: String = """
rootProject.name = "aws-cdk-kotlin-dsl"
"""