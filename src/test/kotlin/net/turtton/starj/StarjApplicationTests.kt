package net.turtton.starj

import net.turtton.starj.storage.port.StorageObjectRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class StarjApplicationTests {

    @MockitoBean
    private lateinit var storageObjectRepository: StorageObjectRepository

    @Test
    fun contextLoads() {
    }

}
