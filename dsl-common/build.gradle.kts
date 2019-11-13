import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("com.jfrog.bintray")
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
    }
}


if (System.getenv("bintrayApiKey") != null || System.getenv()["bintrayApiKey"] != null || project.hasProperty("bintrayApiKey")) {
    val bintrayUser = System.getenv("bintrayUser") ?: System.getenv()["bintrayUser"]
    ?: project.findProperty("bintrayUser") as String
    val bintrayKey = System.getenv("bintrayApiKey") ?: System.getenv()["bintrayApiKey"]
    ?: project.findProperty("bintrayApiKey") as String

    bintray {
        user = bintrayUser
        key = bintrayKey
        setPublications("maven")
        publish = true
        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            userOrg = "justincase"
            repo = "aws-cdk-kotlin-dsl"
            name = "dsl-common"
            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                name = project.version as String
            })
        })
    }
}
