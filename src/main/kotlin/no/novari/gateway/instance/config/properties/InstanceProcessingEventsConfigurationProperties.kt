package no.novari.gateway.instance.config.properties

import no.novari.kafka.topic.configuration.EventCleanupFrequency
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "novari.flyt.instance-gateway.kafka.topic.instance-receival-error")
data class InstanceProcessingEventsConfigurationProperties(
    val retentionTime: Duration,
    val cleanupFrequency: EventCleanupFrequency,
    val partitions: Int,
)
