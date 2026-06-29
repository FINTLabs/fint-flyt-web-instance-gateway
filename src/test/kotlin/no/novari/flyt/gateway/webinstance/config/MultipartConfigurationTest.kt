package no.novari.flyt.gateway.webinstance.config

import jakarta.servlet.MultipartConfigElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class MultipartConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(MultipartConfiguration::class.java),
            )

    @Test
    fun `uses gateway max request size as default multipart limits`() {
        contextRunner.run { context ->
            val multipartConfigElement = context.getBean<MultipartConfigElement>()

            assertThat(multipartConfigElement.maxFileSize).isEqualTo(104_857_600)
            assertThat(multipartConfigElement.maxRequestSize).isEqualTo(104_857_600)
        }
    }

    @Test
    fun `respects explicit Spring multipart limits`() {
        contextRunner
            .withPropertyValues(
                "novari.flyt.web-instance-gateway.max-request-size=150MB",
                "spring.servlet.multipart.max-file-size=10MB",
                "spring.servlet.multipart.max-request-size=20MB",
            ).run { context ->
                val multipartConfigElement = context.getBean<MultipartConfigElement>()

                assertThat(multipartConfigElement.maxFileSize).isEqualTo(10_485_760)
                assertThat(multipartConfigElement.maxRequestSize).isEqualTo(20_971_520)
            }
    }
}
