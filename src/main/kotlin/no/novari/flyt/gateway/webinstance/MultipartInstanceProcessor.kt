package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.exception.AbstractInstanceRejectedException
import no.novari.flyt.gateway.webinstance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.webinstance.exception.MultipartFileUploadException
import no.novari.flyt.gateway.webinstance.exception.NoIntegrationException
import no.novari.flyt.gateway.webinstance.kafka.InstanceReceivalErrorEventProducerService
import no.novari.flyt.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.novari.flyt.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.webinstance.model.Integration
import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import no.novari.flyt.gateway.webinstance.model.MultipartFileUpload
import no.novari.flyt.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationException
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class MultipartInstanceProcessor<T : Any>(
    private val integrationRequestProducerService: IntegrationRequestProducerService,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceReceivalErrorEventProducerService: InstanceReceivalErrorEventProducerService,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
    private val fileClient: FileClient,
    private val sourceApplicationIntegrationIdFunction: (T) -> String,
    private val sourceApplicationInstanceIdFunction: (T) -> String,
    private val multipartInstanceMapper: MultipartInstanceMapper<T>,
) {
    @Value("\${novari.flyt.web-instance-gateway.check-integration-exists:true}")
    var checkIntegrationExists: Boolean = true

    fun processInstance(
        authentication: Authentication,
        incomingInstance: T,
        multipartFiles: Collection<MultipartFile>,
    ): ResponseEntity<Void> {
        return processInstance(incomingInstance, multipartFiles) {
            sourceApplicationAuthorizationService.getSourceApplicationId(authentication)
        }
    }

    fun processInstance(
        sourceApplicationId: Long,
        incomingInstance: T,
        multipartFiles: Collection<MultipartFile>,
    ): ResponseEntity<Void> {
        return processInstance(incomingInstance, multipartFiles) { sourceApplicationId }
    }

    private fun processInstance(
        incomingInstance: T,
        multipartFiles: Collection<MultipartFile>,
        sourceApplicationIdSupplier: () -> Long,
    ): ResponseEntity<Void> {
        val headersBuilder = InstanceFlowHeaders.builder()
        val fileIds = mutableListOf<UUID>()
        val sourceApplicationId: Long
        val sourceApplicationInstanceId: String

        try {
            sourceApplicationId = sourceApplicationIdSupplier()

            val sourceApplicationIntegrationId = sourceApplicationIntegrationIdFunction(incomingInstance)
            sourceApplicationInstanceId = sourceApplicationInstanceIdFunction(incomingInstance)

            headersBuilder.apply {
                correlationId(UUID.randomUUID())
                sourceApplicationId(sourceApplicationId)
                fileIds(fileIds)
                sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                sourceApplicationInstanceId(sourceApplicationInstanceId)
            }

            instanceValidationService.validate(incomingInstance)?.let {
                throw InstanceValidationException(it)
            }

            require(sourceApplicationIntegrationId.isNotBlank()) {
                "sourceApplicationIntegrationId is blank, and was not caught in validation"
            }
            require(sourceApplicationInstanceId.isNotBlank()) {
                "sourceApplicationInstanceId is blank, and was not caught in validation"
            }

            val idPair =
                SourceApplicationIdAndSourceApplicationIntegrationId(
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                )

            if (checkIntegrationExists) {
                val integration =
                    integrationRequestProducerService
                        .get(idPair)
                        ?: throw NoIntegrationException(idPair)
                headersBuilder.integrationId(integration.id)
                if (integration.state == Integration.State.DEACTIVATED) {
                    throw IntegrationDeactivatedException(integration)
                }
            }
        } catch (e: InstanceValidationException) {
            instanceReceivalErrorEventProducerService.publishInstanceValidationErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                buildString {
                    append("Validation error: ")
                    append(e.validationErrors.joinToString { "'${it.fieldPath} ${it.errorMessage}'" })
                },
            )
        } catch (e: NoIntegrationException) {
            instanceReceivalErrorEventProducerService.publishNoIntegrationFoundErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
        } catch (e: IntegrationDeactivatedException) {
            instanceReceivalErrorEventProducerService.publishIntegrationDeactivatedErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message)
        } catch (e: RuntimeException) {
            instanceReceivalErrorEventProducerService.publishGeneralSystemErrorEvent(headersBuilder.build())
            throw e
        }

        return try {
            val multipartFileParts = MultipartFileParts(multipartFiles)
            val instanceObject =
                multipartInstanceMapper.map(
                    sourceApplicationId,
                    incomingInstance,
                ) { fileReference ->
                    val multipartFile = multipartFileParts.resolve(fileReference)
                    val fileUpload =
                        fileReference.toMultipartFileUpload(
                            sourceApplicationId = sourceApplicationId,
                            sourceApplicationInstanceId = sourceApplicationInstanceId,
                            multipartFile = multipartFile,
                        )
                    fileClient
                        .postFile(fileUpload)
                        .also(fileIds::add)
                }
            receivedInstanceEventProducerService.publish(headersBuilder.build(), instanceObject)
            ResponseEntity.accepted().build()
        } catch (e: AbstractInstanceRejectedException) {
            log.error("Instance receival error", e)
            instanceReceivalErrorEventProducerService.publishInstanceRejectedErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message, e)
        } catch (e: MultipartFileUploadException) {
            log.error("File upload error", e)
            instanceReceivalErrorEventProducerService.publishMultipartFileUploadErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
        } catch (e: Exception) {
            log.error("General system error", e)
            instanceReceivalErrorEventProducerService.publishGeneralSystemErrorEvent(headersBuilder.build())
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "General system error", e)
        }
    }

    private fun MultipartFileReference.toMultipartFileUpload(
        sourceApplicationId: Long,
        sourceApplicationInstanceId: String,
        multipartFile: MultipartFile,
    ): MultipartFileUpload =
        MultipartFileUpload(
            name = fileName ?: multipartFile.originalFilename?.takeIf { it.isNotBlank() } ?: partName,
            sourceApplicationId = sourceApplicationId,
            sourceApplicationInstanceId = sourceApplicationInstanceId,
            type = type ?: multipartFile.contentType.toMediaTypeOrDefault(),
            encoding = encoding,
            multipartFile = multipartFile,
        )

    private fun String?.toMediaTypeOrDefault(): MediaType =
        this
            ?.takeIf { it.isNotBlank() }
            ?.let { contentType ->
                runCatching { MediaType.parseMediaType(contentType) }.getOrNull()
            }
            ?: MediaType.APPLICATION_OCTET_STREAM

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
