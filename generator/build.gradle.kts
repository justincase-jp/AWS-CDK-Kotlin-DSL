plugins {
    id("maven-publish")
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
