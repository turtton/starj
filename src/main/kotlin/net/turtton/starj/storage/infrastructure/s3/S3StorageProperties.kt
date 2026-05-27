package net.turtton.starj.storage.infrastructure.s3

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage.s3")
data class S3StorageProperties(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "us-east-1",
    val bucket: String,
)
