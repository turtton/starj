package net.turtton.starj.storage.web

data class RegisterRequest(
    val username: String,
    val password: String,
)

data class RegisterResponse(
    val id: Long,
    val username: String,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class AuthUserResponse(
    val id: Long,
    val username: String,
)
