package net.turtton.starj.config

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
import kotlin.test.assertContains
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:starj-security-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var storageObjectRepository: StorageObjectRepository

    @Test
    fun healthReturnsOkWithoutAuthentication() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun protectedEndpointReturnsUnauthorizedWithoutAuthentication() {
        mockMvc.get("/api/storage")
            .andExpect {
                status { isUnauthorized() }
                header { doesNotExist("Location") }
            }
    }

    @Test
    fun csrfTokenIsSetAsCookieOnRequest() {
        mockMvc.get("/api/auth/csrf")
            .andExpect {
                status { isOk() }
                cookie { exists("XSRF-TOKEN") }
                cookie { httpOnly("XSRF-TOKEN", false) }
            }
    }

    @Test
    fun registerAndLoginArePublicWithoutAuthentication() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"x","password":"short"}"""
            with(csrf())
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"nonexistent","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun apiDocsArePublicAndIncludeAuthAndStoragePaths() {
        val response = mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            }
            .andReturn()
            .response
            .contentAsString

        assertContains(response, "/api/storage")
        assertContains(response, "/api/auth/login")

        val evidenceDir = Path.of(".omo", "evidence")
        Files.createDirectories(evidenceDir)
        Files.writeString(evidenceDir.resolve("task-22-openapi.json"), response)
        Files.writeString(
            evidenceDir.resolve("task-22-docs-security.txt"),
            "GET /v3/api-docs -> 200 OK\nContains /api/storage and /api/auth/login\n",
        )
    }

    @Test
    fun logoutReturnsNoContentWithoutRedirect() {
        mockMvc.post("/api/auth/logout") {
            with(csrf())
        }.andExpect {
            status { isNoContent() }
            header { doesNotExist("Location") }
        }
    }
}
