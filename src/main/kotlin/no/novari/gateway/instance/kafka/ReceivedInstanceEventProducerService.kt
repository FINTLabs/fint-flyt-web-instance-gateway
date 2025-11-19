package no.novari.gateway.instance.kafka

import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.gateway.instance.model.instance.InstanceObject
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ReceivedInstanceEventProducerService(
    instanceFlowTemplateFactory: InstanceFlowTemplateFactory,
    eventTopicService: EventTopicService,
) {
    companion object {
        private const val EVENT_NAME = "instance-received"

        // TODO: Correct retention time?
        private val RETENTION_TIME = Duration.ZERO
        private val CLEANUP_FREQUENCY = EventCleanupFrequency.NORMAL
        private const val PARTITIONS = 1
    }

    private val instanceReceivedTopicNameParameters =
        EventTopicNameParameters
            .builder()
            .topicNamePrefixParameters(
                TopicNamePrefixParameters
                    .stepBuilder()
                    .orgIdApplicationDefault()
                    .domainContextApplicationDefault()
                    .build(),
            ).eventName(EVENT_NAME)
            .build()

    private val template = instanceFlowTemplateFactory.createTemplate(InstanceObject::class.java)

    init {
        eventTopicService.createOrModifyTopic(
            instanceReceivedTopicNameParameters,
            EventTopicConfiguration
                .stepBuilder()
                .partitions(PARTITIONS)
                .retentionTime(RETENTION_TIME)
                .cleanupFrequency(CLEANUP_FREQUENCY)
                .build(),
        )
    }

    fun publish(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObject,
    ) {
        template.send(
            InstanceFlowProducerRecord
                .builder<InstanceObject>()
                .topicNameParameters(instanceReceivedTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .value(instance)
                .build(),
        )
    }
}
