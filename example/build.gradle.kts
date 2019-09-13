import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "jp.justincase"
version = "1.8.0.DEVPREVIEW-0.5.2"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("jp.justincase.aws-cdk-kotlin-dsl", "s3", version as String)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}