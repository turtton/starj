package net.turtton.starj.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.io.Serializable

class UserPrincipal(
    val id: Long,
    private val username: String,
    private val passwordHash: String,
    private val enabled: Boolean,
) : UserDetails, Serializable {

    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = username

    override fun isEnabled(): Boolean = enabled

    fun toCurrentUser(): CurrentUser = CurrentUser(id = id, username = username)

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
