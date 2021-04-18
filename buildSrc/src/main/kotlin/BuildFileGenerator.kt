import data.Version
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.environment.EnvironmentUtils
import java.io.File

object BuildFileGenerator {

    private val ci = System.getenv("CI")?.toBoolean() == true

    @Deprecated("Using Bintray")
    fun generateAndBuildForUnhandledCdkVersions(
        kotlinVersion: String,
        projectVersion: String,
        targetDir: File,
        githubUser: String,
        githubToken: String
    ) {
        generateBuildFilesForUnhandledCdkVersions(
            kotlinVersion = kotlinVersion,
            projectVersion = projectVersion,
            targetDir = targetDir,
            githubUser = githubUser,
            githubToken = githubToken
        )
        runGeneratorsForUnhandledCdkVersions(targetDir = targetDir)
    }

    @Deprecated("Using Bintray")
    fun generateAndBuildForLatestVersion(
        kotlinVersion: String,
        projectVersion: String,
        targetDir: File,
        githubUser: String,
        githubToken: String
    ) {
        BintrayPackageManager.modulesForLatestCdkVersions.let { (version, _) ->
            generateBuildFilesForVersion(
                kotlinVersion = kotlinVersion,
                cdkVersion = version,
                projectVersion = projectVersion,
                targetDir = targetDir,
                githubUser = githubUser,
                githubToken = githubToken,
                generateModules = BintrayPackageManager.cdkModulesForVersion.getValue(version).toList()
                // publishModules = BintrayPackageManager.unhandledCdkModulesForVersions.getValue(version).toList()
            )
            runGeneratorForVersion(
                version,
                targetDir
            )
        }
    }

    @Deprecated("Using Bintray")
    private fun generateBuildFilesForUnhandledCdkVersions(
        kotlinVersion: String,
        projectVersion: String,
        targetDir: File,
        githubUser: String,
        githubToken: String
    ) {
        BintrayPackageManager.unhandledCdkModulesForVersions.forEach { (version, _) ->
            generateBuildFilesForVersion(
                kotlinVersion = kotlinVersion,
                cdkVersion = version,
                projectVersion = projectVersion,
                targetDir = targetDir,
                githubUser = githubUser,
                githubToken = githubToken,
                generateModules = BintrayPackageManager.cdkModulesForVersion.getValue(version).toList()
                // publishModules = BintrayPackageManager.unhandledCdkModulesForVersions.getValue(version).toList()
            )
        }
    }

    private fun generateBuildFilesForVersion(
        kotlinVersion: String,
        cdkVersion: Version,
        projectVersion: String,
        targetDir: File,
        githubUser: String,
        githubToken: String,
        generateModules: List<String>
        // publishModules: List<String>
    ) {
        val generateDir = File(targetDir, cdkVersion.toString())
        generateDir.mkdirs()

        // root build.gradle.kts
        File(generateDir, "build.gradle.kts").apply {
            createNewFile()
            writeText(
                `get root build-gradle-kts file as text`(
                    kotlinVersion = kotlinVersion,
                    cdkVersion = cdkVersion.toString(),
                    projectVersion = projectVersion,
                    generateModules = generateModules,
                    // publishModules = publishModules,
                    githubUser = githubUser,
                    githubToken = githubToken
                )
            )
        }
        // root settings.gradle.kts
        File(generateDir, "settings.gradle.kts").apply {
            createNewFile()
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
                createNewFile()
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
                createNewFile()
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
                createNewFile()
                writeText(
                    `get platform build-gradle-kts file as text`(
                        modules = generateModules
                    )
                )
            }
        }
    }

    @Deprecated("Using Bintray")
    private fun runGeneratorsForUnhandledCdkVersions(
        targetDir: File
    ) {
        BintrayPackageManager.unhandledCdkModulesForVersions.keys.forEach {
            runGeneratorForVersion(
                cdkVersion = it,
                targetDir = targetDir
            )
        }
    }

    private fun runGeneratorForVersion(
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

    @Deprecated("Using Bintray")
    fun publishForUnhandledCdkVersions(
        targetDir: File
    ) {
        BintrayPackageManager.unhandledCdkModulesForVersions.keys.forEach { version ->
            publishForVersion(version, targetDir)
        }
    }

    fun publishForLatestVersion(
        targetDir: File
    ) {
        BintrayPackageManager.modulesForLatestCdkVersions.first.let { version: Version ->
            publishForVersion(version, targetDir)
        }
    }

    private fun publishForVersion(
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
        // publishModules: List<String>,
        githubUser: String,
        githubToken: String
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
            publishing {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/justincase-jp/AWS-CDK-Kotlin-DSL")
                        credentials {
                            username = "$githubUser"
                            password = "$githubToken"
                        }
                    }
                }
            }
        }

        tasks.withType<KotlinCompile> {
                kotlinOptions.jvmTarget = "1.8"
        }

        tasks.register("publishAll") {
            ${generateModules.joinToString(separator = "\n\t") { "dependsOn(\"$it:publish\")" }}
        }
        
        tasks.register("generateAll") {
            ${generateModules.joinToString(separator = "\n\t") { "dependsOn(\"$it-gen:run\")" }}
        }
    """.trimIndent()

    private fun `get root settings-gradle-kts file as text`(
        modules: List<String>
    ) = """
        rootProject.name = "aws-cdk-kotlin-dsl"
        ${modules.joinToString(separator = "\n") { "include(\"$it-gen\", \"$it\")" }}
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

    private fun `get generated build-gradle-kts file as text`(
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
            ${BintrayPackageManager.moduleDependencyMap.getValue(cdkVersion).getValue(cdkModule)
        .joinToString("\n\t") { "api(project(\":$it\"))" }}
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
                ${modules.joinToString(separator = "\n") { "api(\"jp.justincase.aws-cdk-kotlin-dsl:$it:\${project.version}\")" }}
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
