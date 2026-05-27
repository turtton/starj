package net.turtton.starj.storage.web

import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val typeUri: String,
    val title: String,
) {
    BAD_REQUEST(
        HttpStatus.BAD_REQUEST,
        "/problems/bad-request",
        "Bad Request",
    ),

    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "/problems/unauthorized",
        "Unauthorized",
    ),

    FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "/problems/forbidden",
        "Forbidden",
    ),

    NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "/problems/not-found",
        "Not Found",
    ),

    CONFLICT(
        HttpStatus.CONFLICT,
        "/problems/conflict",
        "Conflict",
    ),

    PAYLOAD_TOO_LARGE(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "/problems/payload-too-large",
        "Payload Too Large",
    ),
}
