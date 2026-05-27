package net.turtton.starj.storage.port

interface UserRepository {
    fun findById(id: Long): UserRecord?
    fun findByUsername(username: String): UserRecord?
    fun save(username: String, passwordHash: String): Long
}

data class UserRecord(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val enabled: Boolean,
)
