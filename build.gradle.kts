import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
}

val kotlinVersion: String by project
val awsCdkVersion: String by project

allprojects {
    group = "jp.justincase"
    version = "$awsCdkVersion-0.1.4"

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
    bintray {
        key = bintrayKey
        user = bintrayUser
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

    tasks.register("generateAndUploadForAllModule") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(taskCreateBintrayPackage)
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
} else {
    logger.log(LogLevel.WARN, "Bintray Api-key is not found.")
}
