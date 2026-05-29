package net.turtton.starj.storage.application

import net.turtton.starj.storage.domain.StorageCursor
import net.turtton.starj.storage.web.PaginationConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class CursorPaginationTest {

    @Test
    fun encodeThenDecodeReturnsOriginal() {
        val original = StorageCursor(Instant.parse("2026-01-01T00:00:00Z"), "last-id-123")

        val encoded = CursorPagination.encode(original)
        val decoded = CursorPagination.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun nullSizeReturnsDefaultPageSize() {
        assertEquals(PaginationConstants.DEFAULT_PAGE_SIZE, CursorPagination.validateSize(null))
    }

    @Test
    fun sizeBelowMinimumReturnsMinimum() {
        assertEquals(PaginationConstants.MIN_PAGE_SIZE, CursorPagination.validateSize(PaginationConstants.MIN_PAGE_SIZE - 1))
    }

    @Test
    fun sizeAboveMaximumReturnsMaximum() {
        assertEquals(PaginationConstants.MAX_PAGE_SIZE, CursorPagination.validateSize(PaginationConstants.MAX_PAGE_SIZE + 1))
    }

    @Test
    fun validSizePassesThrough() {
        assertEquals(42, CursorPagination.validateSize(42))
    }

    @Test
    fun cursorIsOpaque() {
        val cursor = StorageCursor(Instant.parse("2026-01-01T00:00:00Z"), "secret-id")

        val encoded = CursorPagination.encode(cursor)

        assertNotEquals(cursor.id, encoded)
    }
}
