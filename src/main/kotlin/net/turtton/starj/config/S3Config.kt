package net.turtton.starj.config

import net.turtton.starj.storage.infrastructure.s3.AwsS3StorageProperties
import net.turtton.starj.storage.infrastructure.s3.MinioS3StorageProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class S3Config {
    @Configuration
    @Profile("production")
    @EnableConfigurationProperties(AwsS3StorageProperties::class)
    class AwsS3Configuration {
        @Bean
        fun awsS3Client(props: AwsS3StorageProperties): S3Client {
            return S3Client.builder()
                .region(Region.of(props.region))
                .build()
        }
    }

    @Configuration
    @Profile("!production")
    @EnableConfigurationProperties(MinioS3StorageProperties::class)
    class MinioS3Configuration {
        @Bean
        fun minioS3Client(props: MinioS3StorageProperties): S3Client {
            return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint))
                .region(Region.of(props.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey, props.secretKey),
                    ),
                )
                .forcePathStyle(true)
                .build()
        }
    }
}
