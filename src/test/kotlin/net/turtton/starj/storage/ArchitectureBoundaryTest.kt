package net.turtton.starj.storage

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.fail

class ArchitectureBoundaryTest {

    private val forbiddenImports = listOf(
        "org.apache.ibatis",
        "org.mybatis",
        "software.amazon.awssdk",
        "io.minio",
        "org.springframework.data.redis",
        "jakarta.servlet",
        "org.springframework.session",
    )

    private val applicationPackageDir = File("src/main/kotlin/net/turtton/starj/storage/application")

    @Test
    fun `application layer must not import infrastructure dependencies`() {
        if (!applicationPackageDir.exists()) return

        val violations = mutableListOf<String>()

        applicationPackageDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNumber, line ->
                    if (line.trimStart().startsWith("import ")) {
                        val importStatement = line.trim().removePrefix("import ").trim()
                        forbiddenImports.forEach { forbidden ->
                            if (importStatement.startsWith(forbidden)) {
                                violations.add("${file.name}:${lineNumber + 1} imports '$importStatement'")
                            }
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "storage.application must NOT depend on infrastructure packages:\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
