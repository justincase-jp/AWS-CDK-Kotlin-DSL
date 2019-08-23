import java.io.File

fun generateBuildFile(
    projectVersion: String,
    cdkVersion: Version?,
    cdkModule: String,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    baseDir: File
) {
    val targetCdkVersion = cdkVersion?.toString() ?: "latest.release"
    val targetDir = File(baseDir, targetCdkVersion)
    targetDir.mkdirs()
    File(targetDir, "build.gradle.kts").apply {
        createNewFile()
        writeText(
            getBuildFileText(
                projectVersion,
                targetCdkVersion,
                cdkModule,
                kotlinVersion,
                bintrayUser,
                bintrayApiKey,
                targetDir
            )
        )
    }
    File(targetDir, "settings.gradle").apply {
        createNewFile()
        writeText(settingsGradleFileText(cdkModule))
    }
    ProcessBuilder("gradle", "run", "clean", "bintrayUpload").run {
        inheritIO()
        directory(targetDir)
        environment()["PATH"] = System.getenv("PATH")
        start()
    }.waitFor()
}

private fun getBuildFileText(
    projectVersion: String,
    cdkVersion: String,
    cdkModule: String,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    targetDir: File
): String = """
import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "jp.justincase.aws-cdk-kotlin-dsl"
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
    implementation("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
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
            repo = "aws-cdk-kotlin-dsl"
            name = "$cdkModule"
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

private fun settingsGradleFileText(module: String) = """
rootProject.name = "$module"
"""