package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerFactory
import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerRecord
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.model.instance.InstanceObject
import no.fintlabs.kafka.topic.EventTopicService
import no.fintlabs.kafka.topic.configuration.CleanupFrequency
import no.fintlabs.kafka.topic.configuration.EventTopicConfiguration
import no.fintlabs.kafka.topic.name.EventTopicNameParameters
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

@Service
class ReceivedInstanceEventProducerService(
    instanceFlowProducerFactory: InstanceFlowEventProducerFactory,
    private val eventTopicService: EventTopicService,
) {
    companion object {
        private const val EVENT_NAME = "instance-received"
        private val TOPIC_RETENTION = 168.days
        private val CLEANUP_FREQUENCY = CleanupFrequency.NORMAL
    }

    private val instanceProducer =
        instanceFlowProducerFactory.createProducer(InstanceObject::class.java)

    private val instanceReceivedTopicNameParameters =
        EventTopicNameParameters
            .builder()
            .eventName(EVENT_NAME)
            .build()
            .also {
                eventTopicService.createOrModifyTopic(
                    it,
                    EventTopicConfiguration
                        .builder()
                        .retentionTime(TOPIC_RETENTION.toJavaDuration())
                        .cleanupFrequency(CLEANUP_FREQUENCY)
                        .build(),
                )
            }

    fun publish(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObject,
    ) {
        instanceProducer.send(
            InstanceFlowEventProducerRecord
                .builder<InstanceObject>()
                .topicNameParameters(instanceReceivedTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .value(instance)
                .build(),
        )
    }
}
