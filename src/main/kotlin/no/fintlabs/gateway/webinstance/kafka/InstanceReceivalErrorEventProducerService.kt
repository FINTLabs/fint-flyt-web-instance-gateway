package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventProducer
import no.fintlabs.flyt.kafka.event.error.InstanceFlowErrorEventProducerRecord
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.ErrorCode
import no.fintlabs.gateway.webinstance.exception.AbstractInstanceRejectedException
import no.fintlabs.gateway.webinstance.exception.FileUploadException
import no.fintlabs.gateway.webinstance.exception.IntegrationDeactivatedException
import no.fintlabs.gateway.webinstance.exception.NoIntegrationException
import no.fintlabs.gateway.webinstance.validation.InstanceValidationErrorMappingService
import no.fintlabs.gateway.webinstance.validation.InstanceValidationException
import no.fintlabs.kafka.model.Error
import no.fintlabs.kafka.model.ErrorCollection
import no.fintlabs.kafka.topic.ErrorEventTopicService
import no.fintlabs.kafka.topic.configuration.CleanupFrequency
import no.fintlabs.kafka.topic.configuration.ErrorEventTopicConfiguration
import no.fintlabs.kafka.topic.name.ErrorEventTopicNameParameters
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class InstanceReceivalErrorEventProducerService(
    private val errorEventTopicService: ErrorEventTopicService,
    private val instanceFlowErrorEventProducer: InstanceFlowErrorEventProducer,
    private val instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
) {
    companion object {
        private const val ERROR_EVENT_NAME = "instance-receival-error"
        private val RETENTION_MS = Duration.ZERO
        private val CLEANUP_FREQUENCY = CleanupFrequency.NORMAL
    }

    private val instanceReceivalErrorTopicNameParameters =
        ErrorEventTopicNameParameters
            .builder()
            .errorEventName(ERROR_EVENT_NAME)
            .build()
            .also {
                errorEventTopicService.createOrModifyTopic(
                    it,
                    ErrorEventTopicConfiguration
                        .builder()
                        .retentionTime(RETENTION_MS)
                        .cleanupFrequency(CLEANUP_FREQUENCY)
                        .build(),
                )
            }

    private fun send(
        headers: InstanceFlowHeaders,
        errors: ErrorCollection,
    ) {
        instanceFlowErrorEventProducer.send(
            InstanceFlowErrorEventProducerRecord
                .builder()
                .topicNameParameters(instanceReceivalErrorTopicNameParameters)
                .instanceFlowHeaders(headers)
                .errorCollection(errors)
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
