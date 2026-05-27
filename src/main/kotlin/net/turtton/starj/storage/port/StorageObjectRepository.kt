package net.turtton.starj.storage.port

import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import java.time.Instant

interface StorageObjectRepository {
    fun findById(id: StorageObjectId, ownerId: OwnerId): StorageObjectRecord?
    fun findByOwner(ownerId: OwnerId, cursor: String?, size: Int): List<StorageObjectRecord>
    fun save(record: StorageObjectRecord)
    fun delete(id: StorageObjectId, ownerId: OwnerId)
}

data class StorageObjectRecord(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val ownerId: Long,
    val createdAt: Instant,
)
