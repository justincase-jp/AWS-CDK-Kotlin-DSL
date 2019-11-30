plugins {
    kotlin("jvm") version "1.3.60"
    id("maven-publish")
    id("com.jfrog.bintray") version "1.8.4"
}

tasks.getByName<Wrapper>("wrapper") {
    gradleVersion = "5.6.2"
}

fun String.removePrefixOrNull(prefix: String): String? =
    takeIf { it.startsWith(prefix) }?.removePrefix(prefix)

val kotlinVersion: String by project
val awsCdkVersion: String by project
val dslVersion =
    System.getenv("CIRCLE_TAG")?.removePrefixOrNull("v")
        ?: System.getenv("CIRCLE_BRANCH")?.removePrefixOrNull("release/")
        ?: "unspecified"

allprojects {
    group = "jp.justincase"
    version = "$awsCdkVersion-$dslVersion"

    repositories {
        mavenCentral()
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
        getCdkUpdatedVersions().forEach { (key, value) ->
            value.forEach { println("$key:$it") }
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
        this.dependsOn(tasks.getByPath(":dsl-generator:publishToMavenLocal"))
        this.dependsOn(tasks.getByPath(":dsl-common:publishToMavenLocal"))
        doLast {
            val versionModuleMap = mutableMapOf<Version, MutableList<String>>()
            cdkLatestUnhandledVersions.forEach { (module, list) ->
                list.onEach {
                    generateBuildFile(
                        project.version as String,
                        it,
                        module,
                        kotlinVersion,
                        bintrayUser,
                        bintrayKey,
                        File(buildDir, "cdkdsl/$module")
                    )
                }.forEach {
                    uploadGeneratedFile(
                        it,
                        module,
                        File(buildDir, "cdkdsl/$module")
                    )
                    if (versionModuleMap.containsKey(it)) {
                        versionModuleMap.getValue(it) += module
                    } else {
                        versionModuleMap[it] = mutableListOf(module)
                    }
                }
            }
            versionModuleMap.forEach { (version, moduleList) ->
                generateAndUploadPlatformModule(
                    moduleList,
                    version,
                    File(buildDir, "cdkdsl/gradle-platform")
                )
            }
        }
    }

    val taskGenerateForAllModuleParallel by tasks.register("generateForAllModuleParallel") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(tasks.getByPath(":dsl-generator:publishToMavenLocal"))
        this.dependsOn(tasks.getByPath(":dsl-common:publishToMavenLocal"))
        doLast {
            generateBuildFiles(
                project.version as String,
                Version(awsCdkVersion),
                kotlinVersion,
                bintrayUser,
                bintrayKey,
                File(buildDir, "cdkdsl")
            )
        }
    }

    val taskGenerateForAllModule by tasks.register("generateForAllModule") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(tasks.getByPath(":dsl-generator:publishToMavenLocal"))
        this.dependsOn(tasks.getByPath(":dsl-common:publishToMavenLocal"))
        doLast {
            cdkModuleList.forEach {
                generateBuildFile(
                    project.version as String,
                    Version(awsCdkVersion),
                    it,
                    kotlinVersion,
                    bintrayUser,
                    bintrayKey,
                    File(buildDir, "cdkdsl/$it")
                )
            }
        }
    }

    tasks.register("uploadToBintrayForAllModuleParallel") {
        this.group = "auto update"
        this.dependsOn(taskCreateBintrayPackage)
        this.dependsOn(taskGenerateForAllModuleParallel)
        doLast {
            uploadGeneratedFiles(
                Version(awsCdkVersion),
                File(buildDir, "cdkdsl")
            )
        }
    }

    tasks.register("uploadToBintrayForAllModule") {
        this.group = "auto update"
        this.dependsOn(taskCreateBintrayPackage)
        this.dependsOn(taskGenerateForAllModule)
        doLast {
            val versionModuleMap = mutableMapOf<Version, MutableList<String>>()
            cdkModuleList.forEach {
                val version = uploadGeneratedFile(
                    Version(awsCdkVersion),
                    it,
                    File(buildDir, "cdkdsl/$it")
                )
                if (versionModuleMap.containsKey(version)) {
                    versionModuleMap.getValue(version) += it
                } else {
                    versionModuleMap[version] = mutableListOf(it)
                }
            }
            versionModuleMap.forEach { (version, moduleList) ->
                generateAndUploadPlatformModule(
                    moduleList,
                    version,
                    File(buildDir, "cdkdsl/gradle-platform")
                )
            }
        }
    }

    tasks.register("generateLambda") {
        this.group = "auto update"
        this.dependsOn(taskCheckCdkUpdate)
        this.dependsOn(tasks.getByPath(":dsl-generator:publishToMavenLocal"))
        this.dependsOn(tasks.getByPath(":dsl-common:publishToMavenLocal"))
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
