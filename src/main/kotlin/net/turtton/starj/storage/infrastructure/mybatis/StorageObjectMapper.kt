package net.turtton.starj.storage.infrastructure.mybatis

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface StorageObjectMapper {
    fun findById(id: String): StorageObjectEntity?
    fun findByIdAndOwnerId(@Param("id") id: String, @Param("ownerId") ownerId: Long): StorageObjectEntity?
    fun insert(entity: StorageObjectEntity): Int
    fun deleteByIdAndOwnerId(@Param("id") id: String, @Param("ownerId") ownerId: Long): Int
    fun findByOwnerIdWithCursor(
        @Param("ownerId") ownerId: Long,
        @Param("cursor") cursor: String?,
        @Param("size") size: Int,
    ): List<StorageObjectEntity>
}

data class StorageObjectEntity(
    val id: String,
    val ownerId: Long,
    val filename: String,
    val contentType: String,
    val size: Long,
    val objectKey: String,
    val createdAt: LocalDateTime? = null,
)
