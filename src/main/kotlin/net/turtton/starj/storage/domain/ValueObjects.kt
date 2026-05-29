package net.turtton.starj.storage.domain

import java.time.Instant

@JvmInline
value class StorageObjectId(val value: String)

@JvmInline
value class OwnerId(val value: Long)

/**
 * Keyset-pagination cursor. Must combine the ordering column (`createdAt`) with a
 * unique tiebreaker (`id`) so paging stays consistent when timestamps collide.
 */
data class StorageCursor(val createdAt: Instant, val id: String)
