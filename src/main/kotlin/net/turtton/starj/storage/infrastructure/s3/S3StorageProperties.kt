package net.turtton.starj.storage.infrastructure.s3

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

sealed interface S3StorageProperties {
    val region: String
    val bucket: String
}

@Profile("production")
@ConfigurationProperties(prefix = "storage.s3")
data class AwsS3StorageProperties(
    override val region: String,
    override val bucket: String,
) : S3StorageProperties

@Profile("!production")
@ConfigurationProperties(prefix = "storage.s3")
data class MinioS3StorageProperties(
    override val region: String,
    override val bucket: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
) : S3StorageProperties
