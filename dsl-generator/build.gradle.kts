plugins {
    id("maven-publish")
}

publishing {
    publications {
        register("maven", MavenPublication::class) {
            groupId = "jp.justincase"
            artifactId = "cdk-dsl-generator"
            version = project.version as String

            from(project.components["java"])
        }
    }
}

repositories {
    maven(url = "https://kotlin.bintray.com/kotlinx")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation(project(":dsl-common"))
}