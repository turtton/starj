package net.turtton.starj

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class IntegrationTestBase {
    companion object {
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("starj_test")
            .withUsername("starj")
            .withPassword("starj")

        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")

        init {
            mysql.start()
            redis.start()
            minio.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            registry.add("storage.s3.endpoint") { minio.s3URL }
            registry.add("storage.s3.access-key") { minio.userName }
            registry.add("storage.s3.secret-key") { minio.password }
            registry.add("storage.s3.region") { "us-east-1" }
            registry.add("storage.s3.bucket") { "starj-test" }
        }
    }
}
