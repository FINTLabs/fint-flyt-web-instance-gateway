package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventProducer
import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventProducerRecord
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.ErrorCode
import no.fintlabs.gateway.webinstance.exception.AbstractInstanceRejectedException
import no.fintlabs.gateway.webinstance.exception.IntegrationDeactivatedException
import no.fintlabs.gateway.webinstance.exception.NoIntegrationException
import no.fintlabs.gateway.webinstance.validation.InstanceValidationErrorMappingService
import no.fintlabs.gateway.webinstance.validation.InstanceValidationException
import no.fintlabs.kafka.event.error.Error
import no.fintlabs.kafka.event.error.ErrorCollection
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicNameParameters
import no.fintlabs.kafka.event.error.topic.ErrorEventTopicService

class InstanceReceivalErrorEventProducerService(
    errorEventTopicService: ErrorEventTopicService,
    private val instanceFlowErrorEventProducer: InstanceFlowErrorEventProducer,
    private val instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
) {

    private val instanceProcessingErrorTopicNameParameters: ErrorEventTopicNameParameters =
        ErrorEventTopicNameParameters.builder()
            .errorEventName("instance-receival-error")
            .build().also { errorEventTopicService.ensureTopic(it, 0) }

    fun publishInstanceValidationErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: InstanceValidationException
    ) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord.builder()
                .topicNameParameters(instanceProcessingErrorTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .errorCollection(instanceValidationErrorMappingService.map(e))
                .build()
        )
    }

    fun publishInstanceRejectedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: AbstractInstanceRejectedException
    ) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord.builder()
                .topicNameParameters(instanceProcessingErrorTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .errorCollection(
                    ErrorCollection(
                        Error.builder()
                            .errorCode(ErrorCode.INSTANCE_REJECTED_ERROR.getCode())
                            .args(mapOf("message" to e.message))
                            .build()
                    )
                )
                .build()
        )
    }

    fun publishNoIntegrationFoundErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: NoIntegrationException
    ) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord.builder()
                .topicNameParameters(instanceProcessingErrorTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .errorCollection(
                    ErrorCollection(
                        Error.builder()
                            .errorCode(ErrorCode.NO_INTEGRATION_FOUND_ERROR.getCode())
                            .args(
                                mapOf(
                                    "sourceApplicationId" to e.sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId.toString(),
                                    "sourceApplicationIntegrationId" to e.sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationIntegrationId
                                )
                            )
                            .build()
                    )
                )
                .build()
        )
    }

    fun publishIntegrationDeactivatedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: IntegrationDeactivatedException
    ) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord.builder()
                .topicNameParameters(instanceProcessingErrorTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .errorCollection(
                    ErrorCollection(
                        Error.builder()
                            .errorCode(ErrorCode.INTEGRATION_DEACTIVATED_ERROR.getCode())
                            .args(
                                mapOf(
                                    "sourceApplicationId" to e.integration.sourceApplicationId.toString(),
                                    "sourceApplicationIntegrationId" to e.integration.sourceApplicationIntegrationId
                                )
                            )
                            .build()
                    )
                )
                .build()
        )
    }

    fun publishGeneralSystemErrorEvent(instanceFlowHeaders: InstanceFlowHeaders) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord.builder()
                .topicNameParameters(instanceProcessingErrorTopicNameParameters)
                .instanceFlowHeaders(instanceFlowHeaders)
                .errorCollection(
                    ErrorCollection(
                        Error.builder()
                            .errorCode(ErrorCode.GENERAL_SYSTEM_ERROR.getCode())
                            .build()
                    )
                )
                .build()
        )
    }

}