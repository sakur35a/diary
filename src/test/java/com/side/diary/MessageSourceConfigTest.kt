package com.side.diary

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.MessageSource
import org.springframework.context.support.ReloadableResourceBundleMessageSource

class MessageSourceConfigTest {
    @Test
    fun `외부 메시지 파일을 수정하면 캐시 만료 후 같은 Bean에서 다시 읽는다`(@TempDir directory: Path) {
        Files.writeString(directory.resolve("messages.properties"), "greeting=Default message\n")
        val korean = directory.resolve("messages_ko.properties")
        Files.writeString(korean, "greeting=변경 전\n")

        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration::class.java))
            .withUserConfiguration(MessageSourceConfig::class.java)
            .withPropertyValues(
                "spring.messages.basename=${directory.resolve("messages").toUri()}",
                "spring.messages.cache-duration=20ms",
                "spring.messages.fallback-to-system-locale=false",
            )
            .run { context ->
                val source =
                    assertIs<ReloadableResourceBundleMessageSource>(
                        context.getBean(MessageSource::class.java)
                    )
                assertEquals(
                    "Default message",
                    source.getMessage("greeting", null, Locale.JAPANESE),
                )
                assertEquals("변경 전", source.getMessage("greeting", null, Locale.KOREAN))

                val modified = Files.getLastModifiedTime(korean).toMillis()
                Files.writeString(korean, "greeting=변경 후\n")
                // 파일시스템의 수정 시각 정밀도와 관계없이 변경을 감지할 수 있게 한다.
                Files.setLastModifiedTime(korean, FileTime.fromMillis(modified + 2000))
                Thread.sleep(50) // 설정한 20ms 캐시의 만료를 기다린다. clearCache()는 호출하지 않는다.

                assertEquals("변경 후", source.getMessage("greeting", null, Locale.KOREAN))
            }
    }
}
