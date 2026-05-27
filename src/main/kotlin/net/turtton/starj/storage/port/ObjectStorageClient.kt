package net.turtton.starj.storage.port

import java.io.InputStream

interface ObjectStorageClient {
    fun upload(key: String, contentType: String, size: Long, inputStream: InputStream)
    fun download(key: String): InputStream
    fun delete(key: String)
}
