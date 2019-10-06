# AWS-CDK-Kotlin-DSL
[![CircleCI](https://circleci.com/gh/justincase-jp/AWS-CDK-Kotlin-DSL/tree/master.svg?style=shield)](
  https://circleci.com/gh/justincase-jp/AWS-CDK-Kotlin-DSL/tree/master
)
[![Download](https://api.bintray.com/packages/justincase/aws-cdk-kotlin-dsl/core/images/download.svg)](
  https://bintray.com/justincase/aws-cdk-kotlin-dsl/core/_latestVersion
)

<a href='https://bintray.com/justincase/aws-cdk-kotlin-dsl/core?source=watch' alt='Get automatic notifications about new "core" versions'>
  <img src='https://www.bintray.com/docs/images/bintray_badge_color.png' height='53' width='62'>
</a>

[**日本語**](README-JA.md)


## Installation
Gradle Kotlin DSL

```kotlin
repositories {
  jcenter()
  maven(url = "https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl")
}

dependencies {
  implementation("jp.justincase.aws-cdk-kotlin-dsl", "$moduleName", "$cdk_version-$dsl_version")
}
```


## Usage
Please see the [`example`](example) directory.
