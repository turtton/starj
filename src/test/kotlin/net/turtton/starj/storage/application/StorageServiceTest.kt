package net.turtton.starj.storage.application

import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.ObjectStorageClient
import net.turtton.starj.storage.port.StorageObjectRecord
import net.turtton.starj.storage.port.StorageObjectRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StorageServiceTest {
    private lateinit var repository: StorageObjectRepository
    private lateinit var objectStorageClient: ObjectStorageClient
    private lateinit var storageService: StorageService

    @BeforeEach
    fun setUp() {
        repository = mock(StorageObjectRepository::class.java)
        objectStorageClient = mock(ObjectStorageClient::class.java)
        storageService = StorageService(repository, objectStorageClient)
    }

    @Test
    fun `upload stores object body before metadata and returns saved record`() {
        val inputStream = ByteArrayInputStream("hello".toByteArray())
        var savedRecord: StorageObjectRecord? = null
        org.mockito.Mockito.doAnswer { invocation ->
            savedRecord = invocation.arguments[0] as StorageObjectRecord
            null
        }.`when`(repository).save(anyStorageObjectRecord())

        val record = storageService.upload(
            ownerId = OwnerId(42L),
            filename = "hello.txt",
            contentType = "text/plain",
            size = 5L,
            inputStream = inputStream,
        )

        UUID.fromString(record.id)
        assertEquals("hello.txt", record.filename)
        assertEquals("text/plain", record.contentType)
        assertEquals(5L, record.size)
        assertEquals(42L, record.ownerId)

        inOrder(objectStorageClient, repository).apply {
            verify(objectStorageClient).upload(record.id, "text/plain", 5L, inputStream)
            verify(repository).save(anyStorageObjectRecord())
        }
        assertEquals(record, savedRecord)
    }

    @Test
    fun `upload deletes object body when metadata save fails`() {
        val inputStream = ByteArrayInputStream("body".toByteArray())
        val dbFailure = IllegalStateException("db down")
        var savedRecord: StorageObjectRecord? = null
        org.mockito.Mockito.doAnswer { invocation ->
            savedRecord = invocation.arguments[0] as StorageObjectRecord
            throw dbFailure
        }.`when`(repository).save(anyStorageObjectRecord())

        val thrown = assertFailsWith<IllegalStateException> {
            storageService.upload(
                ownerId = OwnerId(7L),
                filename = "broken.txt",
                contentType = "text/plain",
                size = 4L,
                inputStream = inputStream,
            )
        }

        assertSame(dbFailure, thrown)
        val compensatedRecord = assertNotNull(savedRecord)
        val key = compensatedRecord.id
        inOrder(objectStorageClient, repository).apply {
            verify(objectStorageClient).upload(key, "text/plain", 4L, inputStream)
            verify(repository).save(compensatedRecord)
            verify(objectStorageClient).delete(key)
        }
    }

    @Test
    fun `other-owner download and detail return null without reading object body`() {
        val id = StorageObjectId("object-1")
        val otherOwner = OwnerId(99L)
        `when`(repository.findById(id, otherOwner)).thenReturn(null)

        assertNull(storageService.download(id, otherOwner))
        assertNull(storageService.detail(id, otherOwner))

        verify(objectStorageClient, never()).download(anyStringArgument())
    }

    @Test
    fun `download returns null when object body is missing`() {
        val id = StorageObjectId("object-2")
        val ownerId = OwnerId(5L)
        val record = storageObjectRecord(id = id.value, ownerId = ownerId.value)
        `when`(repository.findById(id, ownerId)).thenReturn(record)
        doThrow(NoSuchElementException("no such key")).`when`(objectStorageClient).download(record.id)

        assertNull(storageService.download(id, ownerId))

        verify(repository).findById(id, ownerId)
        verify(objectStorageClient).download(record.id)
    }

    @Test
    fun `delete removes metadata first and ignores object body delete failure`() {
        val id = StorageObjectId("object-3")
        val ownerId = OwnerId(11L)
        val record = storageObjectRecord(id = id.value, ownerId = ownerId.value)
        `when`(repository.findById(id, ownerId)).thenReturn(record)
        doThrow(IllegalStateException("s3 unavailable")).`when`(objectStorageClient).delete(record.id)

        assertTrue(storageService.delete(id, ownerId))

        inOrder(repository, objectStorageClient).apply {
            verify(repository).findById(id, ownerId)
            verify(repository).delete(id, ownerId)
            verify(objectStorageClient).delete(record.id)
        }
    }

    private fun storageObjectRecord(
        id: String = "object-id",
        ownerId: Long = 1L,
    ): StorageObjectRecord = StorageObjectRecord(
        id = id,
        filename = "file.txt",
        contentType = "text/plain",
        size = 8L,
        ownerId = ownerId,
        createdAt = Instant.parse("2026-05-27T00:00:00Z"),
    )

    private fun anyStorageObjectRecord(): StorageObjectRecord = any(StorageObjectRecord::class.java)
        ?: storageObjectRecord()

    private fun anyStringArgument(): String = org.mockito.Mockito.anyString() ?: ""
}
