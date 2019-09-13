package jp.justincase.cdkdsl.example

import jp.justincase.cdkdsl.core.Stack
import jp.justincase.cdkdsl.services.s3.Bucket
import jp.justincase.cdkdsl.services.s3.BucketMetrics
import jp.justincase.cdkdsl.services.s3.CfnBucket
import jp.justincase.cdkdsl.services.s3.CfnBucketPropsBuilderScope
import software.amazon.awscdk.core.App
import software.amazon.awscdk.services.s3.BucketEncryption

@Suppress("SuspiciousCollectionReassignment")
fun App.exampleStack() = Stack("example-stack") {}.apply {
    Bucket("MyFirstBucket") {
        versioned = true
        encryption = BucketEncryption.S3_MANAGED
        metrics += BucketMetrics {
            id = "example"
            tagFilters += "a" to "b"
        }
    }

    CfnBucket("MyCfnBucket") {
        bucketName = "cfn-bucket"
        objectLockEnabled = CfnBucketPropsBuilderScope.ObjectLockEnabled.Boolean(true)
        objectLockEnabled = true.toObjectLockEnabled()
        objectLockEnabled /= true
    }
}