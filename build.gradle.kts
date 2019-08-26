import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
}

val kotlinVersion: String by project
val awsCdkVersion: String by project

allprojects {
    group = "jp.justincase"
    version = "$awsCdkVersion-0.2.3"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply<KotlinPluginWrapper>()

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        implementation("com.squareup:kotlinpoet:1.3.0")
        implementation("com.google.guava:guava:28.0-jre")
        // AWS CDK
        implementation("software.amazon.awscdk", "lambda", awsCdkVersion)
        implementation("software.amazon.awscdk", "logs-destinations", awsCdkVersion)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

val taskGetCdkModules by tasks.register("getCdkModules") {
    this.group = "auto update"
    doLast {
        getCdkModules()
    }
}

val taskCheckCdkUpdate by tasks.register("checkCdkUpdate") {
    this.group = "auto update"
    this.dependsOn(taskGetCdkModules)
    doLast {
        cdkModuleList.forEach { module ->
            val list = getCdkUpdatedVersions(module)
            list.forEach {
                println(it)
            }
        }
    }
}

if (System.getenv("bintrayApiKey") != null || System.getenv()["bintrayApiKey"] != null || project.hasProperty("bintrayApiKey")) {
    val bintrayUser = System.getenv("bintrayUser") ?: System.getenv()["bintrayUser"]
    ?: project.findProperty("bintrayUser") as String
    val bintrayKey = System.getenv("bintrayApiKey") ?: System.getenv()["bintrayApiKey"]
    ?: project.findProperty("bintrayApiKey") as String

    val taskCreateBintrayPackage = tasks.register("createBintrayPackage") {
        this.group = "auto update"
        this.dependsOn(taskGetCdkModules)
        doLast {
            createBintrayPackages(bintrayUser, bintrayKey)
        }
    }

    tasks.register("generateAndUploadForUpdatedCdk") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(taskCreateBintrayPackage)
        this.dependsOn(tasks.getByPath(":generator:publishToMavenLocal"))
        doLast {
            updatedCdkVersions.forEach { (module, list) ->
                list.forEach {
                    generateBuildFile(
                        project.version as String,
                        it,
                        module,
                        kotlinVersion,
                        bintrayUser,
                        bintrayKey,
                        File(buildDir, "cdkdsl/$module")
                    )
                }
            }
        }
    }

    val taskGenerateForAllModule by tasks.register("generateForAllModule") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(tasks.getByPath(":generator:publishToMavenLocal"))
        doLast {
            cdkModuleList.forEach {
                generateBuildFile(
                    project.version as String,
                    null,
                    it,
                    kotlinVersion,
                    bintrayUser,
                    bintrayKey,
                    File(buildDir, "cdkdsl/$it")
                )
            }
        }
    }

    tasks.register("uploadToBintrayForAllModule") {
        this.group = "auto update"
        this.dependsOn(taskCreateBintrayPackage)
        this.dependsOn(taskGenerateForAllModule)
        doLast {
            cdkModuleList.forEach {
                uploadGeneratedFile(
                    null,
                    it,
                    File(buildDir, "cdkdsl/$it")
                )
            }
        }
    }

    tasks.register("generateLambda") {
        this.group = "auto update"
        this.dependsOn(tasks.getByPath(":generator:publishToMavenLocal"))
        doLast {
            generateBuildFile(
                project.version as String,
                Version(awsCdkVersion),
                "lambda",
                kotlinVersion,
                bintrayUser,
                bintrayKey,
                File(buildDir, "cdkdsl/lambda")
            )
        }
    }
} else {
    logger.log(LogLevel.WARN, "Bintray Api-key is not found.")
}
