package net.turtton.starj

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("integration")
class IntegrationSmokeTest : IntegrationTestBase() {
    @Test
    fun `mysql container is running`() {
        assertThat(mysql.isRunning).isTrue()
    }

    @Test
    fun `redis container is running`() {
        assertThat(redis.isRunning).isTrue()
    }

    @Test
    fun `minio container is running`() {
        assertThat(minio.isRunning).isTrue()
    }
}
