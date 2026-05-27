package net.turtton.starj.storage.application

import net.turtton.starj.storage.port.UserRecord
import net.turtton.starj.storage.port.UserRepository
import net.turtton.starj.storage.web.RegisterResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(username: String, password: String): RegisterResponse {
        validatePassword(password)
        checkDuplicateUsername(username)

        val encodedPassword = passwordEncoder.encode(password)
            ?: throw IllegalStateException("PasswordEncoder returned null")
        val id = userRepository.save(username, encodedPassword)
        return RegisterResponse(id = id, username = username)
    }

    fun findByUsername(username: String): UserRecord? {
        return userRepository.findByUsername(username)
    }

    private fun validatePassword(password: String) {
        if (password.length < PasswordContract.MIN_LENGTH) {
            throw InvalidPasswordException(
                "Password must be at least ${PasswordContract.MIN_LENGTH} characters",
            )
        }
    }

    private fun checkDuplicateUsername(username: String) {
        if (userRepository.findByUsername(username) != null) {
            throw DuplicateUsernameException("Username '$username' already exists")
        }
    }
}

class InvalidPasswordException(message: String) : RuntimeException(message)

class DuplicateUsernameException(message: String) : RuntimeException(message)
