import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
}

group = "jp.justincase"
version = "0.0.1"

repositories {
    mavenCentral()
}

subprojects {
    apply<KotlinPluginWrapper>()

    group = "jp.justincase"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        implementation("com.squareup:kotlinpoet:1.3.0")
        // AWS CDK
        implementation("software.amazon.awscdk", "lambda", "1.3.0.DEVPREVIEW")
        implementation("software.amazon.awscdk", "logs-destinations", "1.3.0.DEVPREVIEW")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
