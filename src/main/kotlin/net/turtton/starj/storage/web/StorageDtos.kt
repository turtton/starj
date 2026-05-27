package net.turtton.starj.storage.web

import java.time.Instant

data class StorageUploadResponse(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val createdAt: Instant,
)

data class StorageDetailResponse(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val ownerId: Long,
    val createdAt: Instant,
)

data class StorageListResponse(
    val items: List<StorageDetailResponse>,
    val nextCursor: String?,
)
