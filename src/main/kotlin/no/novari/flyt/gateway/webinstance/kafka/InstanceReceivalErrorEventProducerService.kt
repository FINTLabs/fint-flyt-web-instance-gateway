package no.novari.flyt.gateway.webinstance.kafka

import no.novari.flyt.gateway.webinstance.ErrorCode
import no.novari.flyt.gateway.webinstance.config.properties.InstanceProcessingEventsConfigurationProperties
import no.novari.flyt.gateway.webinstance.exception.AbstractInstanceRejectedException
import no.novari.flyt.gateway.webinstance.exception.FileUploadException
import no.novari.flyt.gateway.webinstance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.webinstance.exception.NoIntegrationException
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationErrorMappingService
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationException
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.kafka.topic.ErrorEventTopicService
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.stereotype.Service

@Service
class InstanceReceivalErrorEventProducerService(
    errorEventTopicService: ErrorEventTopicService,
    instanceFlowTemplateFactory: InstanceFlowTemplateFactory,
    private val instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
    instanceProcessingEventsConfigurationProperties: InstanceProcessingEventsConfigurationProperties,
) {
    companion object {
        private const val ERROR_EVENT_NAME = "instance-receival-error"
    }

    private val instanceReceivalErrorTopicNameParameters =
        ErrorEventTopicNameParameters
            .builder()
            .topicNamePrefixParameters(
                TopicNamePrefixParameters
                    .stepBuilder()
                    .orgIdApplicationDefault()
                    .domainContextApplicationDefault()
                    .build(),
            ).errorEventName(ERROR_EVENT_NAME)
            .build()

    private val template = instanceFlowTemplateFactory.createTemplate(ErrorCollection::class.java)

    init {
        // Must match setup in `fint-flyt-instance-gateway`
        errorEventTopicService.createOrModifyTopic(
            instanceReceivalErrorTopicNameParameters,
            EventTopicConfiguration
                .stepBuilder()
                .partitions(instanceProcessingEventsConfigurationProperties.partitions)
                .retentionTime(instanceProcessingEventsConfigurationProperties.retentionTime)
                .cleanupFrequency(instanceProcessingEventsConfigurationProperties.cleanupFrequency)
                .build(),
        )
    }

    private fun send(
        headers: InstanceFlowHeaders,
        errors: ErrorCollection,
    ) {
        template.send(
            InstanceFlowProducerRecord
                .builder<ErrorCollection>()
                .topicNameParameters(instanceReceivalErrorTopicNameParameters)
                .instanceFlowHeaders(headers)
                .value(errors)
                .build(),
        )
    }

    private fun singleError(
        code: ErrorCode,
        args: Map<String, String?> = emptyMap(),
    ): ErrorCollection =
        ErrorCollection(
            Error
                .builder()
                .errorCode(code.getCode())
                .args(args)
                .build(),
        )

    fun publishInstanceValidationErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: InstanceValidationException,
    ) {
        send(instanceFlowHeaders, instanceValidationErrorMappingService.map(e))
    }

    fun publishInstanceRejectedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: AbstractInstanceRejectedException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.INSTANCE_REJECTED_ERROR,
                mapOf("message" to e.message.orEmpty()),
            ),
        )
    }

    fun publishInstanceFileUploadErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: FileUploadException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.FILE_UPLOAD_ERROR,
                mapOf(
                    "name" to e.file.name,
                    "mediatype" to e.file.type.toString(),
                ),
            ),
        )
    }

    fun publishNoIntegrationFoundErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: NoIntegrationException,
    ) {
        val id = e.sourceApplicationIdAndSourceApplicationIntegrationId
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.NO_INTEGRATION_FOUND_ERROR,
                mapOf(
                    "sourceApplicationId" to id.sourceApplicationId.toString(),
                    "sourceApplicationIntegrationId" to id.sourceApplicationIntegrationId,
                ),
            ),
        )
    }

    fun publishIntegrationDeactivatedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: IntegrationDeactivatedException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.INTEGRATION_DEACTIVATED_ERROR,
                mapOf(
                    "sourceApplicationId" to e.integration.sourceApplicationId.toString(),
                    "sourceApplicationIntegrationId" to e.integration.sourceApplicationIntegrationId,
                ),
            ),
        )
    }

    fun publishGeneralSystemErrorEvent(instanceFlowHeaders: InstanceFlowHeaders) {
        send(instanceFlowHeaders, singleError(ErrorCode.GENERAL_SYSTEM_ERROR))
    }
}
