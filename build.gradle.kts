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
    version = "$awsCdkVersion-0.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply<KotlinPluginWrapper>()

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        implementation("com.squareup:kotlinpoet:1.3.0")
        // AWS CDK
        implementation("software.amazon.awscdk", "lambda", awsCdkVersion)
        implementation("software.amazon.awscdk", "logs-destinations", awsCdkVersion)
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.getByPath(":generated:compileKotlin").dependsOn(tasks.getByPath(":generator:run"))

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

val taskCheckCdkUpdate by tasks.register("checkCdkUpdate") {
    this.group = "auto update"
    doLast {
        val list = getCdkUpdatedVersions()
        list.forEach {
            println(it)
        }
    }
}

if (System.getenv("bintray-api-key") != null || System.getenv()["bintray-api-key"] != null || project.hasProperty("bintrayApiKey")) {
    val bintrayUser = System.getenv("bintray-user") ?: System.getenv()["bintray-user"]
    ?: project.findProperty("bintrayUser") as String
    val bintrayKey = System.getenv("bintray-api-key") ?: System.getenv()["bintray-api-key"]
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

    tasks.register("generateForUpdatedCdk") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(tasks.getByPath(":generator:publishToMavenLocal"))
        doLast {
            updatedCdkVersionList.forEach {
                generateBuildFile(
                    project.version as String,
                    it,
                    kotlinVersion,
                    bintrayUser,
                    bintrayKey,
                    File(buildDir, "cdkdsl")
                )
            }
        }
    }
} else {
    logger.log(LogLevel.WARN, "Bintray Api-key is not found.")
}
