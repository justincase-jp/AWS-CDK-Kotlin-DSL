import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "jp.justincase.aws-cdk-kotlin-dsl"
version = (rootProject.version as String).let {
    if (it == "unsupecified") it else it.split("-")[1]
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val taskSourceJar by tasks.register<Jar>("sourcesJar") {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}


publishing {
    publications {
        register("maven", MavenPublication::class) {
            groupId = "jp.justincase.aws-cdk-kotlin-dsl"
            artifactId = "dsl-common"
            version = project.version as String

            from(components["java"])
            artifact(taskSourceJar)
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/justincase-jp/AWS-CDK-Kotlin-DSL")
                credentials {
                    username =
                        System.getenv("GITHUB_USER") ?: "${findProperty("GITHUB_USER") ?: "unset"}"
                    password =
                        System.getenv("GITHUB_TOKEN") ?: "${findProperty("GITHUB_TOKEN") ?: "unset"}"
                }
            }
        }
    }
}
