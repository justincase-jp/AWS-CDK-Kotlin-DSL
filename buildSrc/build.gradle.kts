import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.60"
}

repositories {
    mavenCentral()
    maven(url = "https://kotlin.bintray.com/ktor")
}

dependencies {
    implementation(kotlin("stdlib-jdk8", KotlinVersion.CURRENT.toString()))
    implementation("io.ktor:ktor-client-cio:1.2.5")
    implementation("io.ktor:ktor-client-auth-jvm:1.2.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.+")
    // https://mvnrepository.com/artifact/org.apache.commons/commons-exec
    implementation("org.apache.commons:commons-exec:1.3")

}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()

        apiVersion = ApiVersion.KOTLIN_1_3.versionString
        languageVersion = LanguageVersion.KOTLIN_1_3.versionString
        freeCompilerArgs = listOf("-XXLanguage:+InlineClasses", "-Xuse-experimental=kotlin.Experimental")
    }
}
