package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducer
import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerFactory
import no.fintlabs.flyt.kafka.event.InstanceFlowEventProducerRecord
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.model.instance.InstanceObject
import no.fintlabs.kafka.event.topic.EventTopicNameParameters
import no.fintlabs.kafka.event.topic.EventTopicService
import org.springframework.stereotype.Service

@Service
class ReceivedInstanceEventProducerService(
    instanceFlowProducerFactory: InstanceFlowEventProducerFactory,
    eventTopicService: EventTopicService
) {

    private val instanceProducer: InstanceFlowEventProducer<InstanceObject> =
        instanceFlowProducerFactory.createProducer(InstanceObject::class.java)

    private val formDefinitionEventTopicNameParameters: EventTopicNameParameters =
        EventTopicNameParameters.builder()
            .eventName("instance-received")
            .build().also { eventTopicService.ensureTopic(it, 15778463000L) }

    fun publish(instanceFlowHeaders: InstanceFlowHeaders, instance: InstanceObject) {
        instanceProducer.send(
            InstanceFlowEventProducerRecord.builder<InstanceObject>()
                .topicNameParameters(formDefinitionEventTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .value(instance)
                .build()
        )
    }
}