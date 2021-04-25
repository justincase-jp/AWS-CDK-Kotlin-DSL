import data.Version

plugins {
    kotlin("jvm") version "1.4.10"
    id("maven-publish")
}

tasks.wrapper {
    gradleVersion = "7.0"
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
        val githubUser = System.getenv("GITHUB_USER") ?: System.getenv()["GITHUB_USER"]
        ?: project.findProperty("GITHUB_USER") as String
        val githubKey = System.getenv("GITHUB_TOKEN") ?: System.getenv()["GITHUB_TOKEN"]
        ?: project.findProperty("GITHUB_TOKEN") as String

        val githubCredential = githubUser to githubKey

        create("buildUnhandled") {
            group = "cdk-dsl"
            dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
            dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
            doLastBlocking {
                BuildFileGenerator.buildUnhandled(
                    kotlinVersion,
                    dslVersion,
                    File(buildDir, "cdkdsl"),
                    githubCredential
                )
            }
        }

        if (dslVersion != null) create("publishUnhandled") {
            group = "cdk-dsl"
            dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
            dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
            doLastBlocking {
                BuildFileGenerator.publishUnhandled(
                    kotlinVersion,
                    dslVersion,
                    File(buildDir, "cdkdsl"),
                    githubCredential
                )
            }
        }

        val publishingBranchName = System.getenv("CIRCLE_BRANCH")?.removePrefixOrNull("/publishing")
        if(publishingBranchName != null) {
            create("buildAndPublishSpecifiedVersionForCI") {
                group = "cdk-dsl"
                dependsOn(getByPath(":dsl-generator:publishToMavenLocal"))
                dependsOn(getByPath(":dsl-common:publishToMavenLocal"))
                doLastBlocking {
                    BuildFileGenerator.buildSpecified(
                        kotlinVersion,
                        dslVersion,
                        File(buildDir, "cdkdsl"),
                        githubCredential,
                        Version(publishingBranchName)
                    )
                    BuildFileGenerator.publishSpecified(
                        File(buildDir, "cdkdsl"),
                        Version(publishingBranchName)
                    )
                }
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
