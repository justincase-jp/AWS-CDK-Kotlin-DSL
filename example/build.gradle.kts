plugins {
  kotlin("jvm") version "1.4.32"
  application
}

tasks {
  wrapper {
    gradleVersion = "7.0"
  }

  compileKotlin {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  }

  "run" {
    // Allow skipping code execution conditionally
    outputs.dir("cdk.out")
  }
}
application.mainClass.set("jp.justincase.cdkdsl.example.Main")

repositories {
  mavenCentral()
  maven(url = "https://chamelania.lemm.io")
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("jp.justincase.aws-cdk-kotlin-dsl", "s3", "1.98.0-0.6.11")
}
