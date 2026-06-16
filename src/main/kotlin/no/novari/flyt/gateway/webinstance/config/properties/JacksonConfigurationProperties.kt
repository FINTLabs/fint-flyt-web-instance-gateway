package no.novari.flyt.gateway.webinstance.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties(prefix = "novari.flyt.web-instance-gateway")
data class JacksonConfigurationProperties(
    val maxRequestSize: DataSize = DataSize.ofMegabytes(100),
    val jackson: Jackson = Jackson(),
) {
    fun maxStringLength(): Int = Math.toIntExact((jackson.maxStringLength ?: maxRequestSize).toBytes())

    data class Jackson(
        val maxStringLength: DataSize? = null,
    )
}
