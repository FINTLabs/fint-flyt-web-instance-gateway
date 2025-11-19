package no.fintlabs.gateway.instance

import no.fintlabs.gateway.instance.exception.AbstractInstanceRejectedException
import no.fintlabs.gateway.instance.exception.FileUploadException
import no.fintlabs.gateway.instance.exception.IntegrationDeactivatedException
import no.fintlabs.gateway.instance.exception.NoIntegrationException
import no.fintlabs.gateway.instance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.instance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.instance.model.Integration
import no.fintlabs.gateway.instance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.fintlabs.gateway.instance.validation.InstanceValidationException
import no.fintlabs.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.resourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class InstanceProcessor<T : Any>(
    private val integrationRequestProducerService: IntegrationRequestProducerService,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceReceivalErrorEventProducerService: InstanceReceivalErrorEventProducerService,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
    private val fileClient: FileClient,
    private val sourceApplicationIntegrationIdFunction: (T) -> String,
    private val sourceApplicationInstanceIdFunction: (T) -> String,
    private val instanceMapper: InstanceMapper<T>,
) {
    @Value("\${novari.flyt.instance-gateway.check-integration-exists:true}")
    var checkIntegrationExists: Boolean = true

    fun processInstance(
        authentication: Authentication,
        incomingInstance: T,
    ): ResponseEntity<Void> {
        val headersBuilder = InstanceFlowHeaders.builder()
        val fileIds = mutableListOf<UUID>()
        var sourceApplicationId: Long

        try {
            sourceApplicationId = sourceApplicationAuthorizationService.getSourceApplicationId(authentication)

            val sourceApplicationIntegrationId = sourceApplicationIntegrationIdFunction(incomingInstance)
            val sourceApplicationInstanceId = sourceApplicationInstanceIdFunction(incomingInstance)

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
            val instanceObject =
                instanceMapper.map(
                    sourceApplicationId,
                    incomingInstance,
                ) { file ->
                    fileClient
                        .postFile(file)
                        .also(fileIds::add)
                }
            receivedInstanceEventProducerService.publish(headersBuilder.build(), instanceObject)
            ResponseEntity.accepted().build()
        } catch (e: AbstractInstanceRejectedException) {
            log.error("Instance receival error", e)
            instanceReceivalErrorEventProducerService.publishInstanceRejectedErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.message, e)
        } catch (e: FileUploadException) {
            log.error("File upload error", e)
            instanceReceivalErrorEventProducerService.publishInstanceFileUploadErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
        } catch (e: Exception) {
            log.error("General system error", e)
            instanceReceivalErrorEventProducerService.publishGeneralSystemErrorEvent(headersBuilder.build())
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "General system error", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
