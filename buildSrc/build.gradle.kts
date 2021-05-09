import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `embedded-kotlin`
    scala
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    jcenter() // For Ktor Client 1.3.2
}

dependencies {
    implementation(kotlin("stdlib-jdk8", KotlinVersion.CURRENT.toString()))
    implementation("io.ktor:ktor-client-cio:1.3.2")
    implementation("io.ktor:ktor-client-auth-jvm:1.3.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.+")
    // https://mvnrepository.com/artifact/org.apache.commons/commons-exec
    implementation("org.apache.commons:commons-exec:1.3")

    implementation("org.scala-lang", "scala-library", "2.13.5")
    implementation("io.github.portfoligno.porterie", "porterie_2.13", "0.3.0")
    runtimeOnly("org.slf4j", "slf4j-simple", "1.7.30")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()

        apiVersion = ApiVersion.KOTLIN_1_3.versionString
        languageVersion = LanguageVersion.KOTLIN_1_3.versionString
        freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
    }
}
tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-target:jvm-1.8")
}
