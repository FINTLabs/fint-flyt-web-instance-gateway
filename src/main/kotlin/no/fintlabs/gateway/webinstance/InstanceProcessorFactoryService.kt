package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.webinstance.validation.InstanceValidationService
import no.fintlabs.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
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
