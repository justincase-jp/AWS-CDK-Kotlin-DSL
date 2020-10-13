plugins {
    id("maven-publish")
    kotlin("jvm")
}

val awsCdkVersion: String by project
version = rootProject.version.toString().split("-")[1]

val isCI = System.getenv("CI") == "true"

publishing {
    publications {
        register("maven", MavenPublication::class) {
            groupId = "jp.justincase"
            artifactId = "cdk-dsl-generator"
            version = project.version.toString()

            from(project.components["java"])
        }
    }
}

repositories {
    maven(url = "https://kotlin.bintray.com/kotlinx")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation(project(":dsl-common"))

    implementation("com.squareup:kotlinpoet:1.6.0")
    implementation("com.google.guava:guava:28.2-jre")
    // AWS-CDK/Core, Need to compile
    implementation("software.amazon.awscdk", "core", awsCdkVersion)
    // AWS CDK, Only for dev & debug use
    if (!isCI) {
        implementation("software.amazon.awscdk", "appflow", awsCdkVersion)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}