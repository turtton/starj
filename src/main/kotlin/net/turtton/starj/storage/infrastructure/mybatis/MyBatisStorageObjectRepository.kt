package net.turtton.starj.storage.infrastructure.mybatis

import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.StorageObjectRecord
import net.turtton.starj.storage.port.StorageObjectRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class MyBatisStorageObjectRepository(
    private val mapper: StorageObjectMapper,
) : StorageObjectRepository {

    override fun findById(id: StorageObjectId, ownerId: OwnerId): StorageObjectRecord? {
        return mapper.findByIdAndOwnerId(id.value, ownerId.value)?.toRecord()
    }

    override fun findByOwner(ownerId: OwnerId, cursor: String?, size: Int): List<StorageObjectRecord> {
        return mapper.findByOwnerIdWithCursor(ownerId.value, cursor, size).map { it.toRecord() }
    }

    override fun save(record: StorageObjectRecord) {
        mapper.insert(
            StorageObjectEntity(
                id = record.id,
                ownerId = record.ownerId,
                filename = record.filename,
                contentType = record.contentType,
                size = record.size,
                objectKey = record.id,
                createdAt = LocalDateTime.ofInstant(record.createdAt, ZoneOffset.UTC),
            ),
        )
    }

    override fun delete(id: StorageObjectId, ownerId: OwnerId) {
        mapper.deleteByIdAndOwnerId(id.value, ownerId.value)
    }

    private fun StorageObjectEntity.toRecord(): StorageObjectRecord {
        return StorageObjectRecord(
            id = id,
            filename = filename,
            contentType = contentType,
            size = size,
            ownerId = ownerId,
            createdAt = requireNotNull(createdAt).toInstant(ZoneOffset.UTC),
        )
    }

    private fun LocalDateTime.toInstant(zoneOffset: ZoneOffset): Instant =
        atZone(zoneOffset).toInstant()
}
