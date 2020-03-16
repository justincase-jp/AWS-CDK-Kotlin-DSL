plugins {
    id("maven-publish")
    kotlin("jvm")
}

val awsCdkVersion: String by project
version = rootProject.version.toString().split("-")[1]

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
    implementation(project(":dsl-common"))

    implementation("com.squareup:kotlinpoet:1.5.0")
    implementation("com.google.guava:guava:28.2-jre")
    // AWS CDK
    implementation("software.amazon.awscdk", "lambda", awsCdkVersion)
    implementation("software.amazon.awscdk", "sam", awsCdkVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}