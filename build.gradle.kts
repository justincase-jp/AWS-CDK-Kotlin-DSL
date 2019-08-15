import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
    id("maven-publish")
}

allprojects {
    group = "jp.justincase"
    version = "0.0.1"

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
        implementation("software.amazon.awscdk", "lambda", "1.3.0.DEVPREVIEW")
        implementation("software.amazon.awscdk", "logs-destinations", "1.3.0.DEVPREVIEW")
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
