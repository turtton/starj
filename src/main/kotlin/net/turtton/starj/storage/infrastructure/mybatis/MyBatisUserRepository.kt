package net.turtton.starj.storage.infrastructure.mybatis

import net.turtton.starj.storage.port.UserRecord
import net.turtton.starj.storage.port.UserRepository
import org.springframework.stereotype.Repository

@Repository
class MyBatisUserRepository(
    private val userMapper: UserMapper,
) : UserRepository {

    override fun findById(id: Long): UserRecord? {
        return userMapper.findById(id)?.toRecord()
    }

    override fun findByUsername(username: String): UserRecord? {
        return userMapper.findByUsername(username)?.toRecord()
    }

    override fun save(username: String, passwordHash: String): Long {
        val entity = UserEntity(username = username, passwordHash = passwordHash)
        userMapper.insert(entity)
        return entity.id!!
    }

    private fun UserEntity.toRecord(): UserRecord = UserRecord(
        id = id!!,
        username = username,
        passwordHash = passwordHash,
        enabled = enabled,
    )
}
