package net.turtton.starj.storage

import net.turtton.starj.IntegrationTestBase
import net.turtton.starj.storage.domain.OwnerId
import net.turtton.starj.storage.domain.StorageObjectId
import net.turtton.starj.storage.infrastructure.mybatis.StorageObjectEntity
import net.turtton.starj.storage.infrastructure.mybatis.StorageObjectMapper
import net.turtton.starj.storage.port.StorageObjectRecord
import net.turtton.starj.storage.port.StorageObjectRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.net.URI
import java.time.ZoneOffset
import java.util.UUID

@Tag("integration")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StorageEndToEndTest(
    @Autowired private val s3Client: S3Client,
) : IntegrationTestBase() {

    private val restTemplate = RestTemplate()

    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun createBucket() {
        if (!bucketExists()) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build())
        }
    }

    @Test
    fun `authenticated storage flow preserves session and file bytes`() {
        val username = "e2e-${UUID.randomUUID()}"
        val password = "password123"
        val uploadBytes = "hello authenticated storage ${UUID.randomUUID()}".toByteArray()

        val anonymousCsrf = fetchCsrf()
        val registerResponse = postJson(
            path = "/api/auth/register",
            body = RegisterPayload(username, password),
            csrf = anonymousCsrf,
        )
        assertThat(registerResponse.statusCode).isEqualTo(HttpStatus.CREATED)

        val loginResponse = postJson(
            path = "/api/auth/login",
            body = LoginPayload(username, password),
            csrf = anonymousCsrf,
            csrfCookie = anonymousCsrf.cookie,
        )
        assertThat(loginResponse.statusCode).isEqualTo(HttpStatus.OK)
        val sessionCookie = loginResponse.sessionCookie()
        assertThat(sessionCookie).startsWith("SESSION=")

        val authenticatedCsrf = fetchCsrf(sessionCookie)
        val uploadResponse = upload(uploadBytes, sessionCookie, authenticatedCsrf)
        assertThat(uploadResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val uploaded = uploadResponse.body!!
        assertThat(uploaded.filename).isEqualTo("e2e.txt")
        assertThat(uploaded.contentType).isEqualTo(MediaType.TEXT_PLAIN_VALUE)
        assertThat(uploaded.size).isEqualTo(uploadBytes.size.toLong())

        val listResponse = exchange<StorageListPayload>(
            path = "/api/storage",
            method = HttpMethod.GET,
            sessionCookie = sessionCookie,
        )
        assertThat(listResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(listResponse.body!!.items.map { it.id }).contains(uploaded.id)

        val detailResponse = exchange<StorageDetailPayload>(
            path = "/api/storage/${uploaded.id}",
            method = HttpMethod.GET,
            sessionCookie = sessionCookie,
        )
        assertThat(detailResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(detailResponse.body!!.id).isEqualTo(uploaded.id)
        assertThat(detailResponse.body!!.filename).isEqualTo("e2e.txt")

        val noSessionDetailResponse = exchange<String>(
            path = "/api/storage/${uploaded.id}",
            method = HttpMethod.GET,
        )
        assertThat(noSessionDetailResponse.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

        val downloadResponse = exchange<ByteArray>(
            path = "/api/storage/${uploaded.id}/content",
            method = HttpMethod.GET,
            sessionCookie = sessionCookie,
        )
        assertThat(downloadResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(downloadResponse.body).isEqualTo(uploadBytes)

        val deleteResponse = exchange<Void>(
            path = "/api/storage/${uploaded.id}",
            method = HttpMethod.DELETE,
            sessionCookie = sessionCookie,
            csrf = authenticatedCsrf,
        )
        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        val deletedDetailResponse = exchange<String>(
            path = "/api/storage/${uploaded.id}",
            method = HttpMethod.GET,
            sessionCookie = sessionCookie,
        )
        assertThat(deletedDetailResponse.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        val logoutResponse = postJson(
            path = "/api/auth/logout",
            body = null,
            csrf = authenticatedCsrf,
            sessionCookie = sessionCookie,
            csrfCookie = authenticatedCsrf.cookie,
        )
        assertThat(logoutResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }

    private fun fetchCsrf(sessionCookie: String? = null): CsrfTokenPayload {
        val response = exchange<String>(
            path = "/api/auth/csrf",
            method = HttpMethod.GET,
            sessionCookie = sessionCookie,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val tokenCookie = response.headers[HttpHeaders.SET_COOKIE]
            ?.firstOrNull { it.startsWith("XSRF-TOKEN=") }
            ?: error("CSRF cookie was not returned")
        val token = tokenCookie.substringAfter("XSRF-TOKEN=").substringBefore(";")
        assertThat(token).isNotBlank()
        return CsrfTokenPayload(token = token, headerName = "X-XSRF-TOKEN", cookie = tokenCookie.substringBefore(";"))
    }

    private fun postJson(
        path: String,
        body: Any?,
        csrf: CsrfTokenPayload,
        sessionCookie: String? = null,
        csrfCookie: String? = csrf.cookie,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.add(csrf.headerName, csrf.token)
        headers.addCookies(sessionCookie, csrfCookie)
        return restTemplate.postForEntity(uri(path), HttpEntity(body, headers), String::class.java)
    }

    private fun upload(
        bytes: ByteArray,
        sessionCookie: String,
        csrf: CsrfTokenPayload,
    ): ResponseEntity<StorageUploadPayload> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers.addCookies(sessionCookie, csrf.cookie)
        headers.add(csrf.headerName, csrf.token)

        val file = object : ByteArrayResource(bytes) {
            override fun getFilename(): String = "e2e.txt"
        }
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", file)

        return restTemplate.postForEntity(uri("/api/storage"), HttpEntity(body, headers), StorageUploadPayload::class.java)
    }

    private inline fun <reified T : Any> exchange(
        path: String,
        method: HttpMethod,
        sessionCookie: String? = null,
        csrf: CsrfTokenPayload? = null,
    ): ResponseEntity<T> {
        val headers = HttpHeaders()
        headers.addCookies(sessionCookie, csrf?.cookie)
        csrf?.let { headers.add(it.headerName, it.token) }
        return restTemplate.exchange(uri(path), method, HttpEntity<Unit>(headers), T::class.java)
    }

    private fun HttpHeaders.addCookies(vararg cookies: String?) {
        cookies.filterNotNull()
            .takeIf { it.isNotEmpty() }
            ?.let { add(HttpHeaders.COOKIE, it.joinToString("; ")) }
    }

    private fun ResponseEntity<*>.sessionCookie(): String {
        val setCookie = headers[HttpHeaders.SET_COOKIE]
            ?.firstOrNull { it.startsWith("SESSION=") }
            ?: error("Login did not return SESSION cookie")
        return setCookie.substringBefore(";")
    }

    private fun uri(path: String): URI = URI.create("http://localhost:$port$path")

    private fun bucketExists(): Boolean = runCatching {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET_NAME).build())
    }.isSuccess

    private data class RegisterPayload(val username: String, val password: String)
    private data class LoginPayload(val username: String, val password: String)
    private data class CsrfTokenPayload(val token: String, val headerName: String, val cookie: String)
    private data class StorageUploadPayload(
        val id: String,
        val filename: String,
        val contentType: String,
        val size: Long,
    )

    private data class StorageDetailPayload(
        val id: String,
        val filename: String,
        val contentType: String,
        val size: Long,
        val ownerId: Long,
    )

    private data class StorageListPayload(
        val items: List<StorageDetailPayload>,
        val nextCursor: String?,
    )

    private companion object {
        const val BUCKET_NAME = "starj-test"
    }

    @TestConfiguration
    class TestStorageRepositoryConfiguration {
        @Bean
        @Primary
        fun storageObjectRepository(mapper: StorageObjectMapper): StorageObjectRepository =
            MyBatisStorageObjectRepository(mapper)
    }

    private class MyBatisStorageObjectRepository(
        private val mapper: StorageObjectMapper,
    ) : StorageObjectRepository {
        override fun findById(id: StorageObjectId, ownerId: OwnerId): StorageObjectRecord? =
            mapper.findByIdAndOwnerId(id.value, ownerId.value)?.toRecord()

        override fun findByOwner(ownerId: OwnerId, cursor: String?, size: Int): List<StorageObjectRecord> =
            mapper.findByOwnerIdWithCursor(ownerId.value, cursor, size).map { it.toRecord() }

        override fun save(record: StorageObjectRecord) {
            mapper.insert(record.toEntity())
        }

        override fun delete(id: StorageObjectId, ownerId: OwnerId) {
            mapper.deleteByIdAndOwnerId(id.value, ownerId.value)
        }

        private fun StorageObjectRecord.toEntity(): StorageObjectEntity = StorageObjectEntity(
            id = id,
            ownerId = ownerId,
            filename = filename,
            contentType = contentType,
            size = size,
            objectKey = id,
            createdAt = createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
        )

        private fun StorageObjectEntity.toRecord(): StorageObjectRecord = StorageObjectRecord(
            id = id,
            filename = filename,
            contentType = contentType,
            size = size,
            ownerId = ownerId,
            createdAt = createdAt!!.toInstant(ZoneOffset.UTC),
        )
    }
}
