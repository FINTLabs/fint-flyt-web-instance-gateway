package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.webinstance.validation.InstanceValidationService
import no.fintlabs.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.function.Function

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
        sourceApplicationInstanceIdFunction: Function<T, Optional<String>>,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        val integrationIdFunction =
            Function<T, Optional<String>> {
                Optional.ofNullable(sourceApplicationIntegrationId)
            }

        return createInstanceProcessor(
            integrationIdFunction,
            sourceApplicationInstanceIdFunction,
            instanceMapper,
        )
    }

    fun <T : Any> createInstanceProcessor(
        sourceApplicationIntegrationIdFunction: Function<T, Optional<String>>,
        sourceApplicationInstanceIdFunction: Function<T, Optional<String>>,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        return InstanceProcessor(
            integrationRequestProducerService,
            instanceValidationService,
            receivedInstanceEventProducerService,
            instanceReceivalErrorEventProducerService,
            sourceApplicationAuthorizationService,
            fileClient,
            sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction,
            instanceMapper,
        )
    }
}
