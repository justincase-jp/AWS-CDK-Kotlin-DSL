# AWS-CDK-Kotlin-DSL
[![CircleCI](https://circleci.com/gh/justincase-jp/AWS-CDK-Kotlin-DSL/tree/master.svg?style=shield)](
  https://circleci.com/gh/justincase-jp/AWS-CDK-Kotlin-DSL/tree/master
)
[![Download](https://api.bintray.com/packages/justincase/aws-cdk-kotlin-dsl/core/images/download.svg)](
  https://bintray.com/justincase/aws-cdk-kotlin-dsl/core/_latestVersion
)

このライブラリは、[AWS CDK Java](https://mvnrepository.com/artifact/software.amazon.awscdk)のラッパーライブラリです。  
AWS CDKの各モジュールに対してヘルパー関数・ライブラリ群が自動生成され、Kotlin DSLでインフラ設定が書けるようになります。  
Circle CIにより毎日、日本標準時で午後2時にCDKのアップデートのチェックが行われ、アップデートがあった場合はコード生成・デプロイが行われます。  

<a href='https://bintray.com/justincase/aws-cdk-kotlin-dsl/core?source=watch' alt='Get automatic notifications about new "core" versions'>
  <img src='https://www.bintray.com/docs/images/bintray_badge_color.png' height='53' width='62'>
</a>

[**English**](README.md)


# 使用方法
完全な例は[example](example)モジュールに存在します。
## インストール
**Gradle Kotlin DSL**

```kotlin
val cdkDslVersion: String by project

repositories {
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/justincase/aws-cdk-kotlin-dsl")
    }
}

dependencies {
    implementation("jp.justincase.aws-cdk-kotlin-dsl", "$moduleName", cdkDslVersion)
}
```

AWS-CDK-Kotlin-DSLの各モジュールは、[AWS CDK Java](https://mvnrepository.com/artifact/software.amazon.awscdk)の各モジュールに1:1で対応しています。  
"$moduleName"の部分に適宜必要なモジュール名を補完してください。

## チュートリアル
以下の内容は、[AWS CDKのチュートリアル](https://docs.aws.amazon.com/ja_jp/cdk/latest/guide/getting_started.html#hello_world_tutorial)の様に、S3 Bucketを追加してみます。
### AppとStackの作成
CDKそのもののセットアップの解説は省略します。  
Appを作成し、その下にStackを作成、その後synthするという流れはJavaと同じです。

```kotlin
fun main() {
    App().apply {
        exampleStack()
        synth()
    }
}
```

`exampleStack()` は以下の様に定義します。

```kotlin
import jp.justincase.cdkdsl.core.*

fun App.exampleStack() = Stack("example-stack") {
    // 必要があればStackの設定をここで行います。
    // 無ければ省略可能です。
    // 詳細な説明は省略します。
}.apply {
    // 以後、ここにResourceの設定を足していきます。
}
```

### S3 Bucketの追加
以下のように記述することでS3 Bucketを追加できます。

```kotlin
import jp.justincase.cdkdsl.services.s3.*

Bucket("MyFirstBucket") {
    versioned = true
    encryption = BucketEncryption.S3_MANAGED
}
```

### '+'演算子
CDK Kotlin DSLのスコープ内では、nullableなListとMapに対して+演算子及び+=演算子が利用可能です。  
以下はその利用例です。

```kotlin
Bucket("MyFirstBucket") {
    versioned = true
    encryption = BucketEncryption.S3_MANAGED
    metrics += BucketMetrics {
        id = "example"
        tagFilters += "a" to "b"
    }
}
```

この'+'演算子は、元のList/Mapがnullの時は新しいList/Mapを作って返すという処理を行う拡張関数です。  
そのため、これを使うとIntelliJで警告が出るため、以下のアノテーションをファイルに追加しておくことを推奨します。

```kotlin
@file:Suppress("SuspiciousCollectionReassignment")
```

### Union型
AWS CDKは元はTypeScriptで記述されており、Jsiiというライブラリにより自動生成されたコードを通してJVMからTypeScriptのAPIにアクセスしています。  
そのため、TypeScriptに存在していてもJava/Kotlinでは存在しない言語機能が使われている場合、コードが複雑になる場合があります。  
CDKではいくつかのプロパティに`Union型`というJava/Kotlinに無い機能が使われています。  
これを表現するため、AWS-CDK-Kotlin-DSLではsealed classを用いています。  
そのため、元となるクラスとsealed classへの変換処理が必要になります。  
ここでは、`CfnBucket`クラスを例に該当プロパティの利用方法を見ていきましょう。

```kotlin
CfnBucket("MyCfnBucket") {
    bucketName = "cfn-bucket"
}
```

`CfnBucket`の`objectLockEnabled`というプロパティを例にとってみましょう。  
`objectLockEnabled`プロパティは、元は`Boolean`と`IResolvable`を取りうるUnion型です。  
このプロパティに`true`を代入する事を考えます。  
もっとも安直な方法はコンストラクターを直接呼び出すことです。

```kotlin
objectLockEnabled = CfnBucketPropsBuilderScope.ObjectLockEnabled.Boolean(true)
```

ただし、これは非常に冗長です。  
これよりも非常に単純で古典的な方法があります。  

```kotlin
objectLockEnabled = true.toObjectLockEnabled()
```

最後に、コードは非常に短くなるものの、文脈によってはわかりにくい方法を紹介します。

```kotlin
objectLockEnabled /= true
```

各関数はDSLのスコープ内でのみ利用可能な拡張関数として実装されているため、衝突等の可能性が無く安全です。
