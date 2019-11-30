group = "jp.justincase.aws-cdk-kotlin-dsl"

plugins {
    `java-platform`
    id("maven-publish")
    id("com.jfrog.bintray")
}

dependencies {
    constraints {
        getCdkModules()
        getCdkUpdatedVersions()
        getModuleDependenciesBlocking()
        moduleDependencyMap.forEach { (module, version) ->
            api("jp.justincase.aws-cdk-kotlin-dsl:$module:$version")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("platform") {
            from(components["javaPlatform"])
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
        setPublications("platform")
        publish = true
        pkg(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
            userOrg = "justincase"
            repo = "aws-cdk-kotlin-dsl"
            name = "platform"
            version(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.VersionConfig> {
                name = project.version as String
            })
        })
    }
}
