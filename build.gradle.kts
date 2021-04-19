import data.Version

plugins {
    kotlin("jvm") version "1.4.10"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
}

tasks.getByName<Wrapper>("wrapper") {
    gradleVersion = "6.2.2"
}

fun String.removePrefixOrNull(prefix: String): String? =
    takeIf { it.startsWith(prefix) }?.removePrefix(prefix)

val kotlinVersion: String by project
val awsCdkVersion: String by project
val dslVersion =
    System.getenv("CIRCLE_TAG")?.removePrefixOrNull("v")
        ?: System.getenv("CIRCLE_BRANCH")?.removePrefixOrNull("release/")

allprojects {
    group = "jp.justincase"
    version = "$awsCdkVersion-${dslVersion ?: "unspecified"}"

    repositories {
        mavenCentral()
    }
}

tasks {
    if (System.getenv("GITHUB_TOKEN") != null || System.getenv()["GITHUB_TOKEN"] != null || project.hasProperty("GITHUB_TOKEN")) {
        val bintrayUser = System.getenv("GITHUB_USER") ?: System.getenv()["GITHUB_USER"]
        ?: project.findProperty("GITHUB_USER") as String
        val bintrayKey = System.getenv("GITHUB_TOKEN") ?: System.getenv()["GITHUB_TOKEN"]
        ?: project.findProperty("GITHUB_TOKEN") as String

        val bintrayCredential = bintrayUser to bintrayKey

        create("buildUnhandled") {
            group = "cdk-dsl"
            dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
            dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
            doLastBlocking {
                BuildFileGenerator.buildUnhandled(
                    kotlinVersion,
                    dslVersion,
                    File(buildDir, "cdkdsl"),
                    bintrayCredential
                )
            }
        }

        val createBintrayPackages by creating {
            group = "cdk-dsl"
            doLastBlocking {
                PackageManager.createBintrayPackages(bintrayUser, bintrayKey)
            }
        }

        if (dslVersion != null) create("publishUnhandled") {
            group = "cdk-dsl"
            dependsOn(createBintrayPackages)
            dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
            dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
            doLastBlocking {
                BuildFileGenerator.publishUnhandled(
                    kotlinVersion,
                    dslVersion,
                    File(buildDir, "cdkdsl"),
                    bintrayCredential
                )
            }
        }
    }

    create("buildSpecified") {
        group = "cdk-dsl"
        dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
        dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
        doLastBlocking {
            BuildFileGenerator.buildSpecified(
                kotlinVersion,
                dslVersion,
                File(buildDir, "cdkdsl"),
                null,
                Version(awsCdkVersion)
            )
        }
    }
}
