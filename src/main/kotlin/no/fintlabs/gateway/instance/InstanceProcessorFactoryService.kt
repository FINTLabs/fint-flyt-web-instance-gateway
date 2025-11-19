package no.fintlabs.gateway.instance

import no.fintlabs.gateway.instance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.instance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.resourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.springframework.stereotype.Service

@Service
class InstanceProcessorFactoryService(
    private val integrationRequestProducerService: IntegrationRequestProducerService,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceReceivalErrorEventProducerService: InstanceReceivalErrorEventProducerService,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
    private val fileClient: FileClient,
) {
    fun <T : Any> createInstanceProcessor(
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        return createInstanceProcessor(
            { _ -> sourceApplicationIntegrationId },
            sourceApplicationInstanceIdFunction,
            instanceMapper,
        )
    }

    fun <T : Any> createInstanceProcessor(
        sourceApplicationIntegrationIdFunction: (T) -> String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        return InstanceProcessor(
            integrationRequestProducerService = integrationRequestProducerService,
            instanceValidationService = instanceValidationService,
            receivedInstanceEventProducerService = receivedInstanceEventProducerService,
            instanceReceivalErrorEventProducerService = instanceReceivalErrorEventProducerService,
            sourceApplicationAuthorizationService = sourceApplicationAuthorizationService,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
            instanceMapper = instanceMapper,
        )
    }
}
