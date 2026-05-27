package net.turtton.starj

import net.turtton.starj.storage.port.StorageObjectRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var storageObjectRepository: StorageObjectRepository

    @Test
    fun healthReturnsOk() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                content { string("OK") }
            }
    }
}
