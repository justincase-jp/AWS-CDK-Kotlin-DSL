import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import java.io.File


@KtorExperimentalAPI
fun generateBuildFiles(
    projectVersion: String,
    cdkVersion: Version?,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    baseDir: File
) = runBlocking {
    getModuleDependencies()
    cdkModuleList.map { module ->
        launch(Dispatchers.Default) {
            generateBuildFileInternal(
                projectVersion,
                cdkVersion,
                module,
                kotlinVersion,
                bintrayUser,
                bintrayApiKey,
                File(baseDir, module)
            )
        }
    }.joinAll()
}

@KtorExperimentalAPI
fun generateBuildFile(
    projectVersion: String,
    cdkVersion: Version?,
    cdkModule: String,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    baseDir: File
) = runBlocking {
    getModuleDependencies()
    generateBuildFileInternal(
        projectVersion,
        cdkVersion,
        cdkModule,
        kotlinVersion,
        bintrayUser,
        bintrayApiKey,
        baseDir
    )
}

@KtorExperimentalAPI
suspend fun generateBuildFileInternal(
    projectVersion: String,
    cdkVersion: Version?,
    cdkModule: String,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String,
    baseDir: File
) {
    val targetCdkVersion = (cdkVersion ?: latestDependedCdkVersions.getValue(cdkModule)).toString()
    val targetDir = File(baseDir, targetCdkVersion)
    withContext(Dispatchers.IO) {
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
                    bintrayApiKey
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
                        cdkModule,
                        targetCdkVersion,
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
                    getGeneratedBuildGradleKtsFileText(
                        cdkModule,
                        targetCdkVersion
                    )
                )
            }
        }
    }
    withContext(Dispatchers.IO) {
        println("Code generation for $cdkModule:$targetCdkVersion will be start")
        println("==========".repeat(8))
        val exitCode = ProcessBuilder("gradle", "-S", ":generator:run", ":generated:build").run {
            setupCommand(targetDir)
        }.waitFor()
        println("==========".repeat(8))
        if (exitCode != 0) throw RuntimeException("Process exited with non-zero code: $exitCode. target module is $cdkModule:$cdkVersion")
    }
    println("Code generation for $cdkModule:$targetCdkVersion have done.")
}

private fun ProcessBuilder.setupCommand(targetDir: File): Process {
    directory(targetDir)
    environment()["PATH"] = System.getenv("PATH")
    redirectErrorStream(true)
    return start().apply {
        val reader = inputStream.bufferedReader(Charsets.UTF_8)
        val builder = StringBuilder()
        var c: Int
        while (reader.read().apply { c = this } != -1) {
            builder.append(c.toChar())
        }
        println(builder.toString())
    }
}

fun uploadGeneratedFiles(
    cdkVersion: Version?,
    baseDir: File
) = runBlocking {
    cdkModuleList.map { module ->
        launch(Dispatchers.IO) {
            uploadGeneratedFile(
                cdkVersion,
                module,
                File(baseDir, module)
            )
        }
    }.joinAll()
}

fun uploadGeneratedFile(
    cdkVersion: Version?,
    cdkModule: String,
    baseDir: File
) {
    val targetCdkVersion = (cdkVersion ?: latestDependedCdkVersions.getValue(cdkModule)).toString()
    val targetDir = File(baseDir, targetCdkVersion)
    println("==========".repeat(8))
    val exitCode = ProcessBuilder("gradle", "-S", "bintrayUpload").run {
        setupCommand(targetDir)
    }.waitFor()
    println("==========".repeat(8))
    if (exitCode != 0) throw RuntimeException("Process exited with non-zero code: $exitCode. target module is $cdkModule:$targetCdkVersion")
    println("Upload for $cdkModule:$targetCdkVersion have done.")
}

private fun getRootBuildGradleKtsFileText(
    projectVersion: String,
    cdkVersion: String,
    cdkModule: String,
    kotlinVersion: String,
    bintrayUser: String,
    bintrayApiKey: String
): String = """
import org.w3c.dom.Node
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
            artifact(tasks.getByPath(":generated:sourcesJar"))
            
            pom.withXml {
                val doc = this.asElement().ownerDocument
                fun Node.addDependency(groupId: String, artifactId: String, version: String) {
                    appendChild(doc.createElement("dependency")).apply {
                        appendChild(doc.createElement("groupId").apply { textContent = groupId })
                        appendChild(doc.createElement("artifactId").apply { textContent = artifactId })
                        appendChild(doc.createElement("version").apply { textContent = version })
                        appendChild(doc.createElement("scope").apply { textContent = "compile" })
                    }
                }
                val parent = asElement().getElementsByTagName("dependencies").item(0)
    
                ${moduleDependencyMap.getValue(cdkModule).joinToString("\n                ") {
    """parent.addDependency("jp.justincase.aws-cdk-kotlin-dsl", "$it", "$cdkVersion-${projectVersion
        .split('-')[1]}")"""
}}
            }
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
    cdkModule: String,
    cdkVersion: String,
    targetDir: File
): String = """

plugins {
    id("application")
}

application {
    mainClassName = "jp.justincase.cdkdsl.generator.MainKt"
}

dependencies {
    runtimeOnly("jp.justincase:cdk-dsl-generator:$projectVersion") {
        exclude(group = "software.amazon.awscdk")
    }
    runtimeOnly("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
    // cdk-code is required to run generator
    runtimeOnly("software.amazon.awscdk", "core", "$cdkVersion")
}

tasks.withType<JavaExec> {
    args("$cdkModule", "${targetDir.absolutePath.replace("\\", "\\\\")}")
}
"""

private fun getGeneratedBuildGradleKtsFileText(
    cdkModule: String,
    cdkVersion: String
): String = """
plugins {
    id("java-library")
}

tasks.register<Jar>("sourcesJar") {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

dependencies {
    api("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
}
"""

private fun settingsGradleFileText(module: String) = """
rootProject.name = "$module"
include 'generator'
include 'generated'
"""