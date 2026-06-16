package no.novari.flyt.gateway.webinstance.config

import no.novari.flyt.gateway.webinstance.config.properties.JacksonConfigurationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration(before = [JacksonAutoConfiguration::class])
@EnableConfigurationProperties(JacksonConfigurationProperties::class)
class JacksonConfiguration {
    @Bean
    fun webInstanceGatewayJacksonCustomizer(
        properties: JacksonConfigurationProperties,
    ): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.postConfigurer { objectMapper ->
                objectMapper.factory.setStreamReadConstraints(
                    objectMapper.factory
                        .streamReadConstraints()
                        .rebuild()
                        .maxStringLength(properties.maxStringLength())
                        .build(),
                )
            }
        }
}
