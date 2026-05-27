package net.turtton.starj.storage.application

import net.turtton.starj.storage.web.PaginationConstants
import java.util.Base64

object CursorPagination {
    fun encode(lastId: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(lastId.toByteArray())
    }

    fun decode(cursor: String): String {
        return String(Base64.getUrlDecoder().decode(cursor))
    }

    fun validateSize(size: Int?): Int {
        val s = size ?: PaginationConstants.DEFAULT_PAGE_SIZE
        return s.coerceIn(PaginationConstants.MIN_PAGE_SIZE, PaginationConstants.MAX_PAGE_SIZE)
    }
}
