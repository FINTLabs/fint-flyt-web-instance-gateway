package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerFactory
import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerRecord
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.model.instance.InstanceObject
import no.fintlabs.kafka.event.topic.EventTopicNameParameters
import no.fintlabs.kafka.event.topic.EventTopicService
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.days

@Service
class ReceivedInstanceEventProducerService(
    instanceFlowProducerFactory: InstanceFlowEventProducerFactory,
    private val eventTopicService: EventTopicService,
) {
    companion object {
        private val TOPIC_RETENTION_MS = 168.days.inWholeMilliseconds
    }

    private val instanceProducer =
        instanceFlowProducerFactory.createProducer(InstanceObject::class.java)

    private val instanceReceivedTopicNameParameters =
        EventTopicNameParameters
            .builder()
            .eventName("instance-received")
            .build()
            .also { eventTopicService.ensureTopic(it, TOPIC_RETENTION_MS) }

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
