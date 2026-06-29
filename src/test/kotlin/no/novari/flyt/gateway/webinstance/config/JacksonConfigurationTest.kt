package no.novari.flyt.gateway.webinstance.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class JacksonConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonConfiguration::class.java,
                    JacksonAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `sets default Jackson max string length for large base64 payloads`() {
        contextRunner.run { context ->
            val objectMapper = context.getBean<ObjectMapper>()

            assertThat(objectMapper.factory.streamReadConstraints().maxStringLength)
                .isEqualTo(104_857_600)
        }
    }

    @Test
    fun `uses max request size as default Jackson max string length`() {
        contextRunner
            .withPropertyValues("novari.flyt.web-instance-gateway.max-request-size=150MB")
            .run { context ->
                val objectMapper = context.getBean<ObjectMapper>()

                assertThat(objectMapper.factory.streamReadConstraints().maxStringLength)
                    .isEqualTo(157_286_400)
            }
    }

    @Test
    fun `allows Jackson max string length override`() {
        contextRunner
            .withPropertyValues(
                "novari.flyt.web-instance-gateway.max-request-size=150MB",
                "novari.flyt.web-instance-gateway.jackson.max-string-length=120MB",
            ).run { context ->
                val objectMapper = context.getBean<ObjectMapper>()

                assertThat(objectMapper.factory.streamReadConstraints().maxStringLength)
                    .isEqualTo(125_829_120)
            }
    }
}
