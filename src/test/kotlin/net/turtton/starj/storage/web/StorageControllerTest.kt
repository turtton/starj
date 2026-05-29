package net.turtton.starj.storage.web

import net.turtton.starj.security.UserPrincipal
import net.turtton.starj.storage.application.CursorPagination
import net.turtton.starj.storage.application.StorageService
import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageCursor
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.StorageObjectRecord
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
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
import java.io.ByteArrayInputStream
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:starj-storage-ctrl-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class StorageControllerTest(
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

    private val otherPrincipal = UserPrincipal(
        id = 2L,
        username = "otheruser",
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
    fun `upload returns 201 with file metadata`() {
        val file = MockMultipartFile(
            "file", "test.txt", "text/plain", "hello world".toByteArray(),
        )

        `when`(storageService.upload(
            any() ?: OwnerId(0L),
            anyString() ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )).thenReturn(sampleRecord)

        mockMvc.multipart("/api/storage") {
            file(file)
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value("abc-123") }
            jsonPath("$.filename") { value("test.txt") }
            jsonPath("$.contentType") { value("text/plain") }
            jsonPath("$.size") { value(100) }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @Test
    fun `upload returns 401 without authentication`() {
        val file = MockMultipartFile(
            "file", "test.txt", "text/plain", "hello".toByteArray(),
        )

        mockMvc.multipart("/api/storage") {
            file(file)
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `upload sanitizes filename with path separators`() {
        val file = MockMultipartFile(
            "file", "../etc/passwd", "text/plain", "bad".toByteArray(),
        )

        `when`(storageService.upload(
            any() ?: OwnerId(0L),
            anyString() ?: "",
            anyString() ?: "",
            anyLong(),
            any() ?: System.`in`,
        )).thenReturn(sampleRecord.copy(filename = "..etcpasswd"))

        mockMvc.multipart("/api/storage") {
            file(file)
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `detail returns 200 for owner`() {
        `when`(storageService.detail(StorageObjectId("abc-123"), OwnerId(1L)))
            .thenReturn(sampleRecord)

        mockMvc.get("/api/storage/abc-123") {
            with(user(testPrincipal))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("abc-123") }
            jsonPath("$.filename") { value("test.txt") }
            jsonPath("$.contentType") { value("text/plain") }
            jsonPath("$.size") { value(100) }
            jsonPath("$.ownerId") { value(1) }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @Test
    fun `detail returns 404 for non-owner`() {
        `when`(storageService.detail(StorageObjectId("abc-123"), OwnerId(2L)))
            .thenReturn(null)

        mockMvc.get("/api/storage/abc-123") {
            with(user(otherPrincipal))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `detail returns 404 for missing object`() {
        `when`(storageService.detail(StorageObjectId("nonexistent"), OwnerId(1L)))
            .thenReturn(null)

        mockMvc.get("/api/storage/nonexistent") {
            with(user(testPrincipal))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `detail returns 401 without authentication`() {
        mockMvc.get("/api/storage/abc-123")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `download returns binary content with correct headers`() {
        val content = "file content".toByteArray()
        val stream = ByteArrayInputStream(content)

        `when`(storageService.download(StorageObjectId("abc-123"), OwnerId(1L)))
            .thenReturn(sampleRecord to stream)

        mockMvc.get("/api/storage/abc-123/content") {
            with(user(testPrincipal))
        }.andExpect {
            status { isOk() }
            header { string("Content-Type", "text/plain") }
            header { string("Content-Disposition", """attachment; filename="test.txt"""") }
            content { bytes(content) }
        }
    }

    @Test
    fun `download returns 404 for non-owner`() {
        `when`(storageService.download(StorageObjectId("abc-123"), OwnerId(2L)))
            .thenReturn(null)

        mockMvc.get("/api/storage/abc-123/content") {
            with(user(otherPrincipal))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `download returns 401 without authentication`() {
        mockMvc.get("/api/storage/abc-123/content")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `list returns items with nextCursor when page is full`() {
        val records = listOf(
            sampleRecord,
            sampleRecord.copy(id = "def-456", filename = "second.txt"),
        )

        `when`(storageService.list(OwnerId(1L), null, 2))
            .thenReturn(records)

        mockMvc.get("/api/storage") {
            param("size", "2")
            with(user(testPrincipal))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].id") { value("abc-123") }
            jsonPath("$.items[1].id") { value("def-456") }
            jsonPath("$.nextCursor") {
                value(CursorPagination.encode(StorageCursor(sampleRecord.createdAt, "def-456")))
            }
        }
    }

    @Test
    fun `list returns items without nextCursor when page is not full`() {
        val records = listOf(sampleRecord)

        `when`(storageService.list(OwnerId(1L), null, 20))
            .thenReturn(records)

        mockMvc.get("/api/storage") {
            with(user(testPrincipal))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.nextCursor") { doesNotExist() }
        }
    }

    @Test
    fun `list with valid cursor decodes and passes to service`() {
        val cursorValue = StorageCursor(sampleRecord.createdAt, "abc-123")
        val cursor = CursorPagination.encode(cursorValue)
        val records = listOf(sampleRecord.copy(id = "def-456"))

        `when`(storageService.list(OwnerId(1L), cursorValue, 20))
            .thenReturn(records)

        mockMvc.get("/api/storage") {
            param("cursor", cursor)
            with(user(testPrincipal))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
        }
    }

    @Test
    fun `list with invalid cursor returns 400`() {
        mockMvc.get("/api/storage") {
            param("cursor", "!!!invalid-base64!!!")
            with(user(testPrincipal))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.detail") { value("Invalid cursor") }
        }
    }

    @Test
    fun `list returns 401 without authentication`() {
        mockMvc.get("/api/storage")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `delete returns 204 for owner`() {
        `when`(storageService.delete(StorageObjectId("abc-123"), OwnerId(1L)))
            .thenReturn(true)

        mockMvc.delete("/api/storage/abc-123") {
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isNoContent() }
        }

        verify(storageService).delete(StorageObjectId("abc-123"), OwnerId(1L))
    }

    @Test
    fun `delete returns 404 for non-owner`() {
        `when`(storageService.delete(StorageObjectId("abc-123"), OwnerId(2L)))
            .thenReturn(false)

        mockMvc.delete("/api/storage/abc-123") {
            with(user(otherPrincipal))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `delete returns 404 for missing object`() {
        `when`(storageService.delete(StorageObjectId("nonexistent"), OwnerId(1L)))
            .thenReturn(false)

        mockMvc.delete("/api/storage/nonexistent") {
            with(user(testPrincipal))
            with(csrf())
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `delete returns 401 without authentication`() {
        mockMvc.delete("/api/storage/abc-123") {
            with(csrf())
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
