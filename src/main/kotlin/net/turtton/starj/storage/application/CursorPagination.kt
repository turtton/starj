package net.turtton.starj.storage.application

import net.turtton.starj.storage.domain.StorageCursor
import net.turtton.starj.storage.web.PaginationConstants
import java.time.Instant
import java.util.Base64

object CursorPagination {
    fun encode(cursor: StorageCursor): String {
        val raw = "${cursor.createdAt.toEpochMilli()}:${cursor.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    fun decode(value: String): StorageCursor {
        val raw = String(Base64.getUrlDecoder().decode(value))
        val separator = raw.indexOf(':')
        require(separator > 0 && separator < raw.length - 1) { "Invalid cursor" }
        val epochMilli = raw.substring(0, separator).toLongOrNull()
            ?: throw IllegalArgumentException("Invalid cursor")
        return StorageCursor(Instant.ofEpochMilli(epochMilli), raw.substring(separator + 1))
    }

    fun validateSize(size: Int?): Int {
        val s = size ?: PaginationConstants.DEFAULT_PAGE_SIZE
        return s.coerceIn(PaginationConstants.MIN_PAGE_SIZE, PaginationConstants.MAX_PAGE_SIZE)
    }
}
