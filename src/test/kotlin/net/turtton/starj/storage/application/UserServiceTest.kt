package net.turtton.starj.storage.application

import net.turtton.starj.storage.port.UserRecord
import net.turtton.starj.storage.port.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {

    private lateinit var userService: UserService
    private lateinit var userRepository: FakeUserRepository
    private lateinit var passwordEncoder: FakePasswordEncoder

    @BeforeEach
    fun setUp() {
        userRepository = FakeUserRepository()
        passwordEncoder = FakePasswordEncoder()
        userService = UserService(userRepository, passwordEncoder)
    }

    @Test
    fun `register stores encoded password and returns id with username`() {
        val response = userService.register("alice", "securepass123")

        assertEquals(1L, response.id)
        assertEquals("alice", response.username)

        val saved = userRepository.findByUsername("alice")
        assertNotNull(saved)
        assertTrue(saved.passwordHash.startsWith("{fake}"))
        assertTrue(saved.passwordHash.contains("securepass123"))
    }

    @Test
    fun `register throws DuplicateUsernameException for existing username`() {
        userService.register("bob", "password123")

        assertFailsWith<DuplicateUsernameException> {
            userService.register("bob", "anotherpass1")
        }
    }

    @Test
    fun `register throws InvalidPasswordException for short password`() {
        assertFailsWith<InvalidPasswordException> {
            userService.register("carol", "short")
        }
    }

    @Test
    fun `register throws InvalidPasswordException for password exactly at boundary`() {
        assertFailsWith<InvalidPasswordException> {
            userService.register("dave", "1234567")
        }
    }

    @Test
    fun `register succeeds for password at minimum length`() {
        val response = userService.register("eve", "12345678")
        assertEquals("eve", response.username)
    }

    @Test
    fun `register response does not contain password hash`() {
        val response = userService.register("frank", "longpassword")
        val responseStr = response.toString()
        assertTrue(!responseStr.contains("longpassword"))
        assertTrue(!responseStr.contains("{fake}"))
    }

    @Test
    fun `findByUsername returns null for non-existent user`() {
        assertNull(userService.findByUsername("nobody"))
    }

    @Test
    fun `findByUsername returns record for existing user`() {
        userService.register("grace", "password123")
        val record = userService.findByUsername("grace")
        assertNotNull(record)
        assertEquals("grace", record.username)
    }

    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?): String = "{fake}$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
            encodedPassword == "{fake}$rawPassword"
    }

    private class FakeUserRepository : UserRepository {
        private val store = mutableMapOf<Long, UserRecord>()
        private var nextId = 1L

        override fun findById(id: Long): UserRecord? = store[id]

        override fun findByUsername(username: String): UserRecord? =
            store.values.find { it.username == username }

        override fun save(username: String, passwordHash: String): Long {
            val id = nextId++
            store[id] = UserRecord(id = id, username = username, passwordHash = passwordHash, enabled = true)
            return id
        }
    }
}
