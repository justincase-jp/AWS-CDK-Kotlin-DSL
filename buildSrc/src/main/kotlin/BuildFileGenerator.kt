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
    val targetCdkVersion = (cdkVersion ?: latestCrkVersions.getValue(cdkModule)).toString()
    val targetDir = File(baseDir, targetCdkVersion)
    targetDir.mkdirs()
    File(targetDir, "build.gradle.kts").apply {
        createNewFile()
        writeText(
            getRootBuildGradleKtsFileText(
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
    File(targetDir, "generator").apply {
        mkdirs()
        File(this, "build.gradle.kts").apply {
            createNewFile()
            writeText(
                getGeneratorBuildGradleKtsFileText(
                    projectVersion,
                    File(targetDir, "generated")
                )
            )
        }
    }
    File(targetDir, "generated").apply {
        mkdirs()
        File(this, "build.gradle.kts").apply {
            createNewFile()
            writeText(
                getGeneratedBuildGradleKtsFileText()
            )
        }
    }
    ProcessBuilder("gradle", ":generator:run").run {
        inheritIO()
        directory(targetDir)
        environment()["PATH"] = System.getenv("PATH")
        start()
    }.waitFor()
    println("Code generation for $cdkModule:$targetCdkVersion have done.")
}

fun uploadGeneratedFile(
    cdkVersion: Version?,
    cdkModule: String,
    baseDir: File
) {
    val targetCdkVersion = (cdkVersion ?: latestCrkVersions.getValue(cdkModule)).toString()
    val targetDir = File(baseDir, targetCdkVersion)
    ProcessBuilder("gradle", "bintrayUpload").run {
        inheritIO()
        directory(targetDir)
        environment()["PATH"] = System.getenv("PATH")
        start()
    }.waitFor()
    println("Upload for $cdkModule:$targetCdkVersion have done.")
}

private fun getRootBuildGradleKtsFileText(
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

plugins {
    kotlin("jvm") version "$kotlinVersion"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
}

allprojects {
    group = "jp.justincase.aws-cdk-kotlin-dsl"
    version = "$cdkVersion-${projectVersion.split('-')[1]}"
    
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    apply<KotlinPluginWrapper>()
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    
    dependencies {
        implementation(kotlin("stdlib"))
        // AWS CDK
        api("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
    }
}

tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
}

publishing {
    publications {
        register("maven", MavenPublication::class.java) {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
            
            from(project(":generated").components["java"])
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
"""

private fun getGeneratorBuildGradleKtsFileText(
    projectVersion: String,
    targetDir: File
): String = """

plugins {
    id("application")
}

application {
    mainClassName = "jp.justincase.cdkdsl.generator.MainKt"
}

dependencies {
    runtimeOnly("jp.justincase:cdk-dsl-generator:$projectVersion")
}

tasks.withType<JavaExec> {
    args("${targetDir.absolutePath.replace("\\", "\\\\")}")
}
"""

private fun getGeneratedBuildGradleKtsFileText(): String = """
plugins {
    id("java-library")
}
"""

private fun settingsGradleFileText(module: String) = """
rootProject.name = "$module"
include 'generator'
include 'generated'
"""