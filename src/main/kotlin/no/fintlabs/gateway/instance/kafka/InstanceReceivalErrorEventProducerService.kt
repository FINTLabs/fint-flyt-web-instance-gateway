package no.fintlabs.gateway.instance.kafka

import no.fintlabs.gateway.instance.ErrorCode
import no.fintlabs.gateway.instance.exception.AbstractInstanceRejectedException
import no.fintlabs.gateway.instance.exception.FileUploadException
import no.fintlabs.gateway.instance.exception.IntegrationDeactivatedException
import no.fintlabs.gateway.instance.exception.NoIntegrationException
import no.fintlabs.gateway.instance.validation.InstanceValidationErrorMappingService
import no.fintlabs.gateway.instance.validation.InstanceValidationException
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.model.Error
import no.novari.kafka.model.ErrorCollection
import no.novari.kafka.topic.ErrorEventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.ErrorEventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class InstanceReceivalErrorEventProducerService(
    errorEventTopicService: ErrorEventTopicService,
    instanceFlowTemplateFactory: InstanceFlowTemplateFactory,
    private val instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
) {
    companion object {
        private const val ERROR_EVENT_NAME = "instance-receival-error"

        // TODO: Correct retention time?
        private val RETENTION_TIME = Duration.ZERO
        private val CLEANUP_FREQUENCY = EventCleanupFrequency.NORMAL
        private const val PARTITIONS = 1
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
        errorEventTopicService.createOrModifyTopic(
            instanceReceivalErrorTopicNameParameters,
            EventTopicConfiguration
                .stepBuilder()
                .partitions(PARTITIONS)
                .retentionTime(RETENTION_TIME)
                .cleanupFrequency(CLEANUP_FREQUENCY)
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
