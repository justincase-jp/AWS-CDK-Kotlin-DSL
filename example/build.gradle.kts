plugins {
  kotlin("jvm") version "1.3.50"
}

tasks.compileKotlin {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}


repositories {
  jcenter()
  maven(url = "https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl")
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("jp.justincase.aws-cdk-kotlin-dsl", "s3", "1.8.0.DEVPREVIEW-0.5.2")
}
