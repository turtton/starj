package net.turtton.starj.storage.infrastructure.s3

import net.turtton.starj.storage.port.ObjectStorageClient
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

@Component
class S3ObjectStorageClient(
    private val s3Client: S3Client,
    private val properties: S3StorageProperties,
) : ObjectStorageClient {
    override fun upload(key: String, contentType: String, size: Long, inputStream: InputStream) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build(),
            RequestBody.fromInputStream(inputStream, size),
        )
    }

    override fun download(key: String): InputStream {
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build(),
        )
    }

    override fun delete(key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build(),
        )
    }
}
