
import data.Version
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.environment.EnvironmentUtils
import java.io.File

object BuildFileGenerator {
    private const val t = "    "
    private val ci = System.getenv("CI")?.toBoolean() == true

    suspend fun buildUnhandled(
        kotlinVersion: String,
        projectVersion: String?,
        targetDir: File,
        githubCredential: Pair<String, String>?
    ) =
        PackageManager.unhandledCdkModules().keys.forEach { version ->
            buildSpecified(kotlinVersion, projectVersion, targetDir, githubCredential, version)
        }

    suspend fun buildSpecified(
        kotlinVersion: String,
        projectVersion: String?,
        targetDir: File,
        githubCredential: Pair<String, String>?,
        version: Version
    ) = kotlin.run {
            prepareBuild(
                kotlinVersion = kotlinVersion,
                cdkVersion = version,
                projectVersion = projectVersion ?: "unspecified",
                targetDir = targetDir,
                githubCredential = githubCredential,
                generateModules = PackageManager.cdkModules().getOrDefault(version, listOf()),
                publishModules = githubCredential?.let {
                    projectVersion?.let {
                        PackageManager.getUnpublishedModules(version, Version(it))
                    }
                }
            )
            runBuild(
                version,
                targetDir
            )
        }

    fun publishSpecified(
        targetDir: File,
        version: Version
    ) = kotlin.run {
        publish(version, targetDir)
    }

    private suspend fun prepareBuild(
        kotlinVersion: String,
        cdkVersion: Version,
        projectVersion: String,
        targetDir: File,
        githubCredential: Pair<String, String>?,
        generateModules: List<String>,
        publishModules: List<String>?
    ) {
        val generateDir = File(targetDir, cdkVersion.toString())
        generateDir.mkdirs()

        // root build.gradle.kts
        File(generateDir, "build.gradle.kts").apply {
            writeText(
                `get root build-gradle-kts file as text`(
                    kotlinVersion = kotlinVersion,
                    cdkVersion = cdkVersion.toString(),
                    projectVersion = projectVersion,
                    generateModules = generateModules,
                    publishModules = publishModules,
                    githubCredential = githubCredential
                )
            )
        }
        // root settings.gradle.kts
        File(generateDir, "settings.gradle.kts").apply {
            writeText(
                `get root settings-gradle-kts file as text`(
                    modules = generateModules
                )
            )
        }

        // for each cdk module
        generateModules.forEach { module ->
            val moduleDir = File(generateDir, module)
            moduleDir.mkdirs()
            // generated build.gradle.kts
            File(moduleDir, "build.gradle.kts").apply {
                writeText(
                    `get generated build-gradle-kts file as text`(
                        cdkModule = module,
                        cdkVersion = cdkVersion,
                        projectVersion = projectVersion
                    )
                )
            }
            val generatorDir = File(generateDir, "$module-gen")
            generatorDir.mkdirs()
            // generator build.gradle.kts
            File(generatorDir, "build.gradle.kts").apply {
                writeText(
                    `get generator build-gradle-kts file as text`(
                        cdkModule = module,
                        cdkVersion = cdkVersion.toString(),
                        projectVersion = projectVersion,
                        targetDir = moduleDir
                    )
                )
            }
        }

        // gradle-platform
        File(generateDir, "platform").let { platformDir ->
            platformDir.mkdirs()
            File(platformDir, "build.gradle.kts").apply {
                writeText(
                    `get platform build-gradle-kts file as text`(
                        modules = generateModules
                    )
                )
            }
        }
    }

    private fun runBuild(
        cdkVersion: Version,
        targetDir: File
    ) {
        println("Start generation and build for cdk version $cdkVersion")
        val executor = DefaultExecutor()
        try {
            executor.setExitValue(0)
            executor.workingDirectory = File(targetDir, cdkVersion.toString())
            executor.execute(CommandLine.parse("gradle -S generateAll build $parallelIfNotCi"), EnvironmentUtils.getProcEnvironment())
            executor.watchdog
        } catch (e: Exception) {
            throw e
        }
        println("Completed generation and build for cdk version $cdkVersion")
    }

    suspend fun publishUnhandled(
        kotlinVersion: String,
        projectVersion: String,
        targetDir: File,
        bintrayCredential: Pair<String, String>
    ) {
        PackageManager.unhandledCdkModules().keys.forEach { version ->
            buildSpecified(kotlinVersion, projectVersion, targetDir, bintrayCredential, version)
            publish(version, targetDir)
        }
    }

    private fun publish(
        cdkVersion: Version,
        targetDir: File
    ) {
        println("Start publishing for cdk version $cdkVersion")
        val executor = DefaultExecutor()
        try {
            executor.setExitValue(0)
            executor.workingDirectory = File(targetDir, cdkVersion.toString())
            executor.execute(CommandLine.parse("gradle -S publishAll $parallelIfNotCi"))
        } catch (e: Exception) {
            throw e
        }
        println("Completed publishing for cdk version $cdkVersion")
    }

    private val parallelIfNotCi: String by lazy { if (!ci) "--parallel" else "" }

    // Build File Templates

    private fun `get root build-gradle-kts file as text`(
        kotlinVersion: String,
        cdkVersion: String,
        projectVersion: String,
        generateModules: List<String>,
        publishModules: List<String>?,
        githubCredential: Pair<String, String>?
    ) = """
        import org.w3c.dom.Node
        import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

        plugins {
            kotlin("jvm") version "$kotlinVersion"
            `maven-publish`
        }

        allprojects {
            apply(plugin = "maven-publish")
            group = "jp.justincase.aws-cdk-kotlin-dsl"
            version = "$cdkVersion-$projectVersion"
            
            repositories {
                mavenCentral()
                mavenLocal()
            }
            ${githubCredential?.let { (githubUser, githubToken) -> """publishing {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        isAllowInsecureProtocol = true
                        url = uri("http://localhost:38877")
                        credentials {
                            username = "$githubUser"
                            password = "$githubToken"
                        }
                    }
                }
            }""" }}
        }

        tasks.withType<KotlinCompile> {
                kotlinOptions.jvmTarget = "1.8"
        }

        ${publishModules?.let { """tasks.register("publishAll") {
            ${publishModules.joinToString(separator = "\n$t$t$t") { """dependsOn("$it:publish")""" }}
            dependsOn(":platform:publish")
        }""" } ?: "" }
        
        tasks.register("generateAll") {
            ${generateModules.joinToString(separator = "\n$t$t$t") { """dependsOn("$it-gen:run")""" }}
        }
    """.trimIndent()

    private fun `get root settings-gradle-kts file as text`(
        modules: List<String>
    ) = """
        rootProject.name = "aws-cdk-kotlin-dsl"
        ${modules.joinToString(separator = "\n$t$t") { """include("$it-gen", "$it")""" }}
        include("platform")
    """.trimIndent()

    private fun `get generator build-gradle-kts file as text`(
        cdkModule: String,
        cdkVersion: String,
        projectVersion: String,
        targetDir: File
    ) = """
        plugins {
            application
        }

        application {
            mainClassName = "jp.justincase.cdkdsl.generator.MainKt"
        }

        dependencies {
            runtimeOnly("jp.justincase:cdk-dsl-generator:$projectVersion") {
                exclude(group = "software.amazon.awscdk")
            }
            runtimeOnly("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
            // cdk-core is required to run generator
            runtimeOnly("software.amazon.awscdk", "core", "$cdkVersion")
        }

        tasks.withType<JavaExec> {
            args("$cdkModule", "${targetDir.absolutePath.replace("\\", "\\\\")}")
        }
    """.trimIndent()

    private suspend fun `get generated build-gradle-kts file as text`(
        cdkModule: String,
        cdkVersion: Version,
        projectVersion: String
    ) = """
        plugins {
            id("java-library")
            kotlin("jvm")
        }

        tasks.register<Jar>("sourcesJar") {
            from(sourceSets.main.get().allSource)
            archiveClassifier.set("sources")
        }
        
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "1.8"
        }

        dependencies {
            implementation(kotlin("stdlib"))
            implementation("jp.justincase.aws-cdk-kotlin-dsl:dsl-common:$projectVersion")
            api("software.amazon.awscdk", "$cdkModule", "$cdkVersion")
            implementation("software.amazon.awscdk", "core", "$cdkVersion")
            ${
                PackageManager
                    .moduleDependency(cdkVersion)
                    .getOrDefault(cdkModule, listOf())
                    .joinToString("\n$t$t$t") { """api(project(":$it"))""" }
            }
        }
        
        publishing {
            publications {
                register("maven", MavenPublication::class.java) {
                    groupId = project.group.toString()
                    artifactId = project.name
                    version = project.version.toString()
                    
                    from(project.components["java"])
                    artifact(tasks.getByPath(":$cdkModule:sourcesJar"))
                }
            }
        }
    """.trimIndent()

    private fun `get platform build-gradle-kts file as text`(
        modules: List<String>
    ) = """
        plugins {
            `java-platform`
            id("maven-publish")
        }
        
        dependencies {
            constraints {
                ${modules.joinToString(separator = "\n$t$t$t$t") {
                    """api("jp.justincase.aws-cdk-kotlin-dsl:$it:${'$'}{project.version}")"""
                }}
            }
        }
        
        publishing {
            publications {
                create<MavenPublication>("platform") {
                    artifactId = "gradle-platform"
                    from(components["javaPlatform"])
                }
            }
        }
    """.trimIndent()
}
