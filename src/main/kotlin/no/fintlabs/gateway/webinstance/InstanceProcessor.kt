package no.fintlabs.gateway.webinstance

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders
import no.fintlabs.gateway.webinstance.exception.AbstractInstanceRejectedException
import no.fintlabs.gateway.webinstance.exception.FileUploadException
import no.fintlabs.gateway.webinstance.exception.IntegrationDeactivatedException
import no.fintlabs.gateway.webinstance.exception.NoIntegrationException
import no.fintlabs.gateway.webinstance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.webinstance.model.Integration
import no.fintlabs.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.fintlabs.gateway.webinstance.validation.InstanceValidationException
import no.fintlabs.gateway.webinstance.validation.InstanceValidationService
import no.fintlabs.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import java.util.function.Function

class InstanceProcessor<T : Any>(
    private val integrationRequestProducerService: IntegrationRequestProducerService,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceReceivalErrorEventProducerService: InstanceReceivalErrorEventProducerService,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
    private val fileClient: FileClient,
    private val sourceApplicationIntegrationIdFunction: Function<T, Optional<String>>,
    private val sourceApplicationInstanceIdFunction: Function<T, Optional<String>>,
    private val instanceMapper: InstanceMapper<T>,
) {
    @Value("\${fint.flyt.webinstance-gateway.check-integration-exists:true}")
    var checkIntegrationExists: Boolean = true

    private val log: Logger = LoggerFactory.getLogger(InstanceProcessor::class.java)

    fun processInstance(
        authentication: Authentication,
        incomingInstance: T,
    ): ResponseEntity<Any> {
        val headersBuilder = InstanceFlowHeaders.builder()
        val fileIds: MutableList<UUID> = ArrayList()
        var sourceApplicationId: Long

        try {
            sourceApplicationId = sourceApplicationAuthorizationService.getSourceApplicationId(authentication)

            headersBuilder.correlationId(UUID.randomUUID())
            headersBuilder.sourceApplicationId(sourceApplicationId)
            headersBuilder.fileIds(fileIds)

            val sourceApplicationIntegrationIdOptional = sourceApplicationIntegrationIdFunction.apply(incomingInstance)
            val sourceApplicationInstanceIdOptional = sourceApplicationInstanceIdFunction.apply(incomingInstance)

            sourceApplicationIntegrationIdOptional.ifPresent { headersBuilder.sourceApplicationIntegrationId(it) }
            sourceApplicationInstanceIdOptional.ifPresent { headersBuilder.sourceApplicationInstanceId(it) }

            sourceApplicationIntegrationIdOptional.ifPresent { integrationId ->
                if (integrationId.isNotBlank()) {
                    val sourceApplicationIntegrationId =
                        SourceApplicationIdAndSourceApplicationIntegrationId(
                            sourceApplicationId = sourceApplicationId,
                            sourceApplicationIntegrationId = integrationId,
                        )

                    if (checkIntegrationExists) {
                        val integration =
                            integrationRequestProducerService
                                .get(sourceApplicationIntegrationId)
                                .orElseThrow { NoIntegrationException(sourceApplicationIntegrationId) }
                        headersBuilder.integrationId(integration.id)
                        if (integration.state == Integration.State.DEACTIVATED) {
                            throw IntegrationDeactivatedException(integration)
                        }
                    }
                }
            }

            instanceValidationService.validate(incomingInstance).ifPresent {
                throw InstanceValidationException(it)
            }

            if (sourceApplicationIntegrationIdOptional.isEmpty) {
                throw IllegalStateException(
                    "sourceApplicationIntegrationIdOptional is empty, and was not caught in validation",
                )
            }
            if (sourceApplicationInstanceIdOptional.isEmpty) {
                throw IllegalStateException(
                    "sourceApplicationInstanceIdOptional is empty, and was not caught in validation",
                )
            }
        } catch (e: InstanceValidationException) {
            instanceReceivalErrorEventProducerService.publishInstanceValidationErrorEvent(headersBuilder.build(), e)
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation error" +
                    if (e.validationErrors.size > 1) {
                        "s:"
                    } else {
                        ":" +
                            e.validationErrors.joinToString { "'${it.fieldPath} ${it.errorMessage}'" }
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
                    val fileId = fileClient.postFile(file)
                    fileIds.add(fileId)
                    fileId
                }
            receivedInstanceEventProducerService.publish(headersBuilder.build(), instanceObject)
            ResponseEntity.accepted().build()
        } catch (e: AbstractInstanceRejectedException) {
            log.error("Instance receival error", e)
            instanceReceivalErrorEventProducerService.publishInstanceRejectedErrorEvent(headersBuilder.build(), e)
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.message)
        } catch (e: FileUploadException) {
            log.error("File upload error", e)
            instanceReceivalErrorEventProducerService.publishInstanceFileUploadErrorEvent(headersBuilder.build(), e)
            ResponseEntity.internalServerError().body(e.message)
        } catch (e: Exception) {
            log.error("General system error", e)
            instanceReceivalErrorEventProducerService.publishGeneralSystemErrorEvent(headersBuilder.build())
            ResponseEntity.internalServerError().build()
        }
    }
}
