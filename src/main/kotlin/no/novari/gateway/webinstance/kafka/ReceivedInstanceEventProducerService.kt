package no.novari.gateway.webinstance.kafka

import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.gateway.webinstance.model.instance.InstanceObject
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.stereotype.Service

@Service
class ReceivedInstanceEventProducerService(
    instanceFlowTemplateFactory: InstanceFlowTemplateFactory,
) {
    companion object {
        private const val EVENT_NAME = "instance-received"
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
