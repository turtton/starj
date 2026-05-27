package net.turtton.starj.storage.infrastructure.s3

import net.turtton.starj.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.io.ByteArrayInputStream

@Tag("integration")
@SpringBootTest
class S3ObjectStorageClientIntegrationTest(
    @Autowired private val objectStorageClient: S3ObjectStorageClient,
    @Autowired private val s3Client: S3Client,
    @Autowired private val properties: S3StorageProperties,
) : IntegrationTestBase() {
    @BeforeEach
    fun createBucket() {
        if (!bucketExists()) {
            s3Client.createBucket(
                CreateBucketRequest.builder()
                    .bucket(properties.bucket)
                    .build(),
            )
        }
    }

    @Test
    fun `upload then download returns same bytes`() {
        val key = "integration/hello.txt"
        val bytes = "hello minio".toByteArray()

        objectStorageClient.upload(key, "text/plain", bytes.size.toLong(), ByteArrayInputStream(bytes))

        val downloaded = objectStorageClient.download(key).use { it.readBytes() }
        assertThat(downloaded).isEqualTo(bytes)
    }

    @Test
    fun `upload then delete then download throws`() {
        val key = "integration/delete-me.txt"
        val bytes = "delete me".toByteArray()

        objectStorageClient.upload(key, "text/plain", bytes.size.toLong(), ByteArrayInputStream(bytes))
        objectStorageClient.delete(key)

        assertThatThrownBy { objectStorageClient.download(key).use { it.readBytes() } }
            .isInstanceOf(NoSuchKeyException::class.java)
    }

    @Test
    fun `upload zero-byte file works`() {
        val key = "integration/empty.txt"
        val bytes = ByteArray(0)

        objectStorageClient.upload(key, "text/plain", 0, ByteArrayInputStream(bytes))

        val downloaded = objectStorageClient.download(key).use { it.readBytes() }

        assertThat(downloaded).isEmpty()
    }

    private fun bucketExists(): Boolean {
        return runCatching {
            s3Client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(properties.bucket)
                    .build(),
            )
        }.isSuccess
    }
}
