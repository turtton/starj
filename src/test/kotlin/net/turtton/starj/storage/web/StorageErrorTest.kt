package net.turtton.starj.storage.web

import net.turtton.starj.security.UserPrincipal
import net.turtton.starj.storage.application.StorageService
import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.StorageObjectRecord
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:starj-storage-error-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class StorageErrorTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var storageService: StorageService

    private val testPrincipal = UserPrincipal(
        id = 1L,
        username = "testuser",
        passwordHash = "hashed",
        enabled = true,
    )

    private val sampleRecord = StorageObjectRecord(
        id = "abc-123",
        filename = "test.txt",
        contentType = "text/plain",
        size = 100L,
        ownerId = 1L,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `invalid cursor returns ProblemDetail with type, title, status, detail`() {
        mockMvc.get("/api/storage") {
            param("cursor", "not-valid-base64!!!")
            with(user(testPrincipal))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("/problems/bad-request") }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.detail") { value("Invalid cursor") }
        }
    }

    @Test
    fun `upload without CSRF token returns 403`() {
        val file = MockMultipartFile(
            "file", "test.txt", "text/plain", "content".toByteArray(),
        )

        mockMvc.multipart("/api/storage") {
            file(file)
            with(user(testPrincipal))
            // no csrf()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `delete without CSRF token returns 403`() {
        mockMvc.delete("/api/storage/abc-123") {
            with(user(testPrincipal))
            // no csrf()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `oversized upload returns 413 ProblemDetail`() {
        val file = MockMultipartFile(
            "file", "large.bin", "application/octet-stream", "data".toByteArray(),
        )

        `when`(storageService.upload(
            any() ?: OwnerId(0L),
            anyString() ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )).thenThrow(MaxUploadSizeExceededException(50_000_000L))

        mockMvc.multipart("/api/storage") {
            file(file)
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isContentTooLarge() }
            jsonPath("$.type") { value("/problems/payload-too-large") }
            jsonPath("$.title") { value("Payload Too Large") }
            jsonPath("$.status") { value(413) }
            jsonPath("$.detail") { value("File size exceeds the maximum allowed size of 50MB") }
        }
    }

    @Test
    fun `zero-byte upload returns 201`() {
        val emptyFile = MockMultipartFile(
            "file", "empty.txt", "text/plain", ByteArray(0),
        )

        `when`(storageService.upload(
            any() ?: OwnerId(0L),
            anyString() ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )).thenReturn(sampleRecord.copy(filename = "empty.txt", size = 0L))

        mockMvc.multipart("/api/storage") {
            file(emptyFile)
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.filename") { value("empty.txt") }
            jsonPath("$.size") { value(0) }
        }
    }

    @Test
    fun `upload with path traversal filename is sanitized`() {
        val maliciousFile = MockMultipartFile(
            "file", "../../etc/passwd", "text/plain", "malicious".toByteArray(),
        )

        `when`(storageService.upload(
            any() ?: OwnerId(0L),
            anyString() ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )).thenReturn(sampleRecord.copy(filename = "....etcpasswd"))

        mockMvc.multipart("/api/storage") {
            file(maliciousFile)
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }

        verify(storageService).upload(
            any() ?: OwnerId(0L),
            eq("....etcpasswd") ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )
    }

    @Test
    fun `download returns 404 when S3 object missing behind DB record`() {
        `when`(storageService.download(StorageObjectId("exists-in-db"), OwnerId(1L)))
            .thenReturn(null)

        mockMvc.get("/api/storage/exists-in-db/content") {
            with(user(testPrincipal))
        }.andExpect {
            status { isNotFound() }
        }
    }

}
