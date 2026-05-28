package net.turtton.starj.storage.infrastructure.mybatis

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Select

@Mapper
interface UserMapper {
    // language=mysql
    @Select(
        """
        SELECT id, username, password_hash, enabled, created_at
        FROM users
        WHERE username = #{username}
        """,
    )
    fun findByUsername(username: String): UserEntity?

    // language=mysql
    @Select(
        """
        SELECT id, username, password_hash, enabled, created_at
        FROM users
        WHERE id = #{id}
        """,
    )
    fun findById(id: Long): UserEntity?

    // language=mysql
    @Insert(
        """
        INSERT INTO users (username, password_hash, enabled, created_at)
        VALUES (#{username}, #{passwordHash}, #{enabled}, #{createdAt})
        """,
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(entity: UserEntity): Int
}

data class UserEntity(
    val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val enabled: Boolean = true,
    val createdAt: java.time.LocalDateTime? = null,
)
