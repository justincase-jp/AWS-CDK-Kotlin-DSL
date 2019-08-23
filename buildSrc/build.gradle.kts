import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `embedded-kotlin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-client-cio:1.2.3")
    implementation("io.ktor:ktor-client-auth-jvm:1.2.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.+")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()

        apiVersion = ApiVersion.KOTLIN_1_3.versionString
        languageVersion = LanguageVersion.KOTLIN_1_3.versionString
        freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
    }
}
