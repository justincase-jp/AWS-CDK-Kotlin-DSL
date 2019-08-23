plugins {
    id("application")
    id("maven-publish")
}

application {
    mainClassName = "jp.justincase.cdkdsl.generator.MainKt"
}

publishing {
    publications {
        register("maven", MavenPublication::class.java) {
            groupId = "jp.justincase"
            artifactId = "cdk-dsl-generator"
            version = project.version as String

            from(project.components["java"])
        }
    }
}
