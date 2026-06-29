package no.novari.flyt.gateway.webinstance.config

import jakarta.servlet.MultipartConfigElement
import no.novari.flyt.gateway.webinstance.config.properties.JacksonConfigurationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration(before = [MultipartAutoConfiguration::class])
@ConditionalOnProperty(prefix = "spring.servlet.multipart", name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(
    value = [
        MultipartProperties::class,
        JacksonConfigurationProperties::class,
    ],
)
class MultipartConfiguration {
    @Bean
    @ConditionalOnMissingBean(MultipartConfigElement::class)
    fun webInstanceGatewayMultipartConfigElement(
        gatewayProperties: JacksonConfigurationProperties,
        multipartProperties: MultipartProperties,
        environment: Environment,
    ): MultipartConfigElement {
        val factory = MultipartConfigFactory()
        factory.setMaxFileSize(
            if (environment.containsProperty("spring.servlet.multipart.max-file-size")) {
                multipartProperties.maxFileSize ?: gatewayProperties.maxRequestSize
            } else {
                gatewayProperties.maxRequestSize
            },
        )
        factory.setMaxRequestSize(
            if (environment.containsProperty("spring.servlet.multipart.max-request-size")) {
                multipartProperties.maxRequestSize ?: gatewayProperties.maxRequestSize
            } else {
                gatewayProperties.maxRequestSize
            },
        )
        multipartProperties.fileSizeThreshold?.let(factory::setFileSizeThreshold)
        multipartProperties.location
            ?.takeIf { it.isNotBlank() }
            ?.let(factory::setLocation)
        return factory.createMultipartConfig()
    }
}
