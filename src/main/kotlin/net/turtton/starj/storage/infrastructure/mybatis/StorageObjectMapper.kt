package net.turtton.starj.storage.infrastructure.mybatis

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.LocalDateTime

@Mapper
interface StorageObjectMapper {
    // language=mysql
    @Select(
        """
        SELECT id, owner_id, filename, content_type, size, object_key, created_at
        FROM storage_objects
        WHERE id = #{id}
        """,
    )
    fun findById(id: String): StorageObjectEntity?

    // language=mysql
    @Select(
        """
        SELECT id, owner_id, filename, content_type, size, object_key, created_at
        FROM storage_objects
        WHERE id = #{id}
          AND owner_id = #{ownerId}
        """,
    )
    fun findByIdAndOwnerId(@Param("id") id: String, @Param("ownerId") ownerId: Long): StorageObjectEntity?

    // language=mysql
    @Insert(
        """
        INSERT INTO storage_objects (id, owner_id, filename, content_type, size, object_key, created_at)
        VALUES (#{id}, #{ownerId}, #{filename}, #{contentType}, #{size}, #{objectKey}, #{createdAt})
        """,
    )
    fun insert(entity: StorageObjectEntity): Int

    // language=mysql
    @Delete(
        """
        DELETE FROM storage_objects
        WHERE id = #{id}
          AND owner_id = #{ownerId}
        """,
    )
    fun deleteByIdAndOwnerId(@Param("id") id: String, @Param("ownerId") ownerId: Long): Int

    // language=mysql
    @Select(
        """
        SELECT id, owner_id, filename, content_type, size, object_key, created_at
        FROM storage_objects
        WHERE owner_id = #{ownerId}
          AND (#{cursor} IS NULL OR id < #{cursor})
        ORDER BY created_at DESC
        LIMIT #{size}
        """,
    )
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
