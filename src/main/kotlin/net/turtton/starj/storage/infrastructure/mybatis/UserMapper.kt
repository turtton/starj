package net.turtton.starj.storage.infrastructure.mybatis

import org.apache.ibatis.annotations.Mapper

@Mapper
interface UserMapper {
    fun findByUsername(username: String): UserEntity?
    fun findById(id: Long): UserEntity?
    fun insert(entity: UserEntity): Int
}

data class UserEntity(
    val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val enabled: Boolean = true,
    val createdAt: java.time.LocalDateTime? = null,
)
