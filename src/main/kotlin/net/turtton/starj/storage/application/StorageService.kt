package net.turtton.starj.storage.application

import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageCursor
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.ObjectStorageClient
import net.turtton.starj.storage.port.StorageObjectRecord
import net.turtton.starj.storage.port.StorageObjectRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.Instant
import java.util.UUID

@Service
class StorageService(
    private val storageObjectRepository: StorageObjectRepository,
    private val objectStorageClient: ObjectStorageClient,
) {
    fun upload(
        ownerId: OwnerId,
        filename: String,
        contentType: String,
        size: Long,
        inputStream: InputStream,
    ): StorageObjectRecord {
        val key = UUID.randomUUID().toString()
        val record = StorageObjectRecord(
            id = key,
            filename = filename,
            contentType = contentType,
            size = size,
            ownerId = ownerId.value,
            createdAt = Instant.now(),
        )

        objectStorageClient.upload(key, contentType, size, inputStream)

        try {
            storageObjectRepository.save(record)
        } catch (exception: RuntimeException) {
            deleteUploadedObjectAfterFailedSave(key, exception)
            throw exception
        }

        return record
    }

    fun download(id: StorageObjectId, ownerId: OwnerId): Pair<StorageObjectRecord, InputStream>? {
        val record = storageObjectRepository.findById(id, ownerId) ?: return null

        val stream = try {
            objectStorageClient.download(record.id)
        } catch (exception: RuntimeException) {
            logger.warn(
                "Storage object metadata exists but object body is missing: id={}, ownerId={}",
                record.id,
                ownerId.value,
                exception,
            )
            return null
        }

        return record to stream
    }

    fun detail(id: StorageObjectId, ownerId: OwnerId): StorageObjectRecord? =
        storageObjectRepository.findById(id, ownerId)

    fun list(ownerId: OwnerId, cursor: StorageCursor?, size: Int): List<StorageObjectRecord> {
        return storageObjectRepository.findByOwner(ownerId, cursor, size)
    }

    fun delete(id: StorageObjectId, ownerId: OwnerId): Boolean {
        val record = storageObjectRepository.findById(id, ownerId) ?: return false

        storageObjectRepository.delete(id, ownerId)

        try {
            objectStorageClient.delete(record.id)
        } catch (exception: RuntimeException) {
            logger.warn(
                "Failed to delete object body after metadata deletion: id={}, ownerId={}",
                record.id,
                ownerId.value,
                exception,
            )
        }

        return true
    }

    private fun deleteUploadedObjectAfterFailedSave(key: String, saveException: RuntimeException) {
        try {
            objectStorageClient.delete(key)
        } catch (deleteException: RuntimeException) {
            logger.warn(
                "Failed to delete uploaded object after metadata save failure: id={}",
                key,
                deleteException,
            )
            saveException.addSuppressed(deleteException)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(StorageService::class.java)
    }
}
