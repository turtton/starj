package net.turtton.starj.storage.web

import net.turtton.starj.security.UserPrincipal
import net.turtton.starj.storage.application.CursorPagination
import net.turtton.starj.storage.application.StorageService
import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.port.StorageObjectRecord
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@RestController
@RequestMapping("/api/storage")
@Tag(name = "Storage", description = "File storage endpoints")
class StorageController(
    private val storageService: StorageService,
) {

    @PostMapping
    @Operation(summary = "Upload a file")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<StorageUploadResponse> {
        val filename = sanitizeFilename(file.originalFilename ?: "unnamed")
        val contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        val size = file.size
        val ownerId = OwnerId(principal.id)

        val record = storageService.upload(ownerId, filename, contentType, size, file.inputStream)

        return ResponseEntity.status(HttpStatus.CREATED).body(record.toUploadResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get file metadata")
    fun detail(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<StorageDetailResponse> {
        val record = storageService.detail(StorageObjectId(id), OwnerId(principal.id))
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(record.toDetailResponse())
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download a file")
    fun download(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<InputStreamResource> {
        val (record, stream) = storageService.download(StorageObjectId(id), OwnerId(principal.id))
            ?: return ResponseEntity.notFound().build()

        val disposition = ContentDisposition.attachment()
            .filename(record.filename)
            .build()

        val headers = HttpHeaders()
        headers.contentDisposition = disposition
        headers.contentType = MediaType.parseMediaType(record.contentType)
        headers.contentLength = record.size

        return ResponseEntity.ok()
            .headers(headers)
            .body(InputStreamResource(stream))
    }

    @GetMapping
    @Operation(summary = "List files")
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<*> {
        val validatedSize = CursorPagination.validateSize(size)
        val decodedCursor = if (cursor != null) {
            try {
                CursorPagination.decode(cursor)
            } catch (_: IllegalArgumentException) {
                val problem = ProblemDetail.forStatus(ErrorType.BAD_REQUEST.status)
                problem.type = URI.create(ErrorType.BAD_REQUEST.typeUri)
                problem.title = ErrorType.BAD_REQUEST.title
                problem.detail = "Invalid cursor"
                return ResponseEntity.badRequest().body(problem)
            }
        } else {
            null
        }

        val ownerId = OwnerId(principal.id)
        val records = storageService.list(ownerId, decodedCursor, validatedSize)

        val nextCursor = if (records.size == validatedSize) {
            CursorPagination.encode(records.last().id)
        } else {
            null
        }

        val items = records.map { it.toDetailResponse() }
        return ResponseEntity.ok(StorageListResponse(items = items, nextCursor = nextCursor))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a file")
    fun delete(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        val deleted = storageService.delete(StorageObjectId(id), OwnerId(principal.id))
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatus(ErrorType.PAYLOAD_TOO_LARGE.status)
        problem.type = URI.create(ErrorType.PAYLOAD_TOO_LARGE.typeUri)
        problem.title = ErrorType.PAYLOAD_TOO_LARGE.title
        problem.detail = "File size exceeds the maximum allowed size of 50MB"
        return ResponseEntity.status(ErrorType.PAYLOAD_TOO_LARGE.status).body(problem)
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace("/", "")
            .replace("\\", "")
            .replace("\u0000", "")
            .ifBlank { "unnamed" }
    }

    private fun StorageObjectRecord.toUploadResponse() = StorageUploadResponse(
        id = id,
        filename = filename,
        contentType = contentType,
        size = size,
        createdAt = createdAt,
    )

    private fun StorageObjectRecord.toDetailResponse() = StorageDetailResponse(
        id = id,
        filename = filename,
        contentType = contentType,
        size = size,
        ownerId = ownerId,
        createdAt = createdAt,
    )
}
