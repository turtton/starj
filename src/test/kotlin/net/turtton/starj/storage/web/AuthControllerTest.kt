package net.turtton.starj.storage.web

import net.turtton.starj.storage.port.StorageObjectRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:starj-auth-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var storageObjectRepository: StorageObjectRepository

    @Test
    fun `register returns 201 with id and username`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"newuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.username") { value("newuser") }
        }
    }

    @Test
    fun `register returns 409 for duplicate username`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"dupuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"dupuser","password":"password456"}"""
            with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.type") { value("/problems/conflict") }
            jsonPath("$.title") { value("Conflict") }
        }
    }

    @Test
    fun `register returns 400 for short password`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"shortpw","password":"short"}"""
            with(csrf())
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("/problems/bad-request") }
            jsonPath("$.title") { value("Bad Request") }
        }
    }

    @Test
    fun `login returns 200 with user info and session cookie`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"loginuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"loginuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.username") { value("loginuser") }
        }
    }

    @Test
    fun `login returns 401 for wrong credentials`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"authuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"authuser","password":"wrongpassword"}"""
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.type") { value("/problems/unauthorized") }
            jsonPath("$.title") { value("Unauthorized") }
        }
    }

    @Test
    fun `logout returns 204 and invalidates session`() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"logoutuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        val loginResult = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"logoutuser","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val sessionCookie = loginResult.response.getCookie("SESSION")
            ?: loginResult.response.getCookie("JSESSIONID")

        mockMvc.post("/api/auth/logout") {
            with(csrf())
            if (sessionCookie != null) {
                cookie(sessionCookie)
            }
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `me returns 401 without authentication`() {
        mockMvc.get("/api/auth/me")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `csrf endpoint returns 200`() {
        mockMvc.get("/api/auth/csrf")
            .andExpect {
                status { isOk() }
            }
    }
}
