package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.novari.flyt.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationService
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.springframework.stereotype.Service

@Service
class InstanceProcessorFactoryService(
    private val integrationRequestProducerService: IntegrationRequestProducerService,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceErrorEventProducerService: InstanceErrorEventProducerService,
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
            instanceErrorEventProducerService = instanceErrorEventProducerService,
            sourceApplicationAuthorizationService = sourceApplicationAuthorizationService,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
            instanceMapper = instanceMapper,
        )
    }

    fun <T : Any> createMultipartInstanceProcessor(
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        multipartInstanceMapper: MultipartInstanceMapper<T>,
    ): MultipartInstanceProcessor<T> {
        return createMultipartInstanceProcessor(
            { _ -> sourceApplicationIntegrationId },
            sourceApplicationInstanceIdFunction,
            multipartInstanceMapper,
        )
    }

    fun <T : Any> createMultipartInstanceProcessor(
        sourceApplicationIntegrationIdFunction: (T) -> String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        multipartInstanceMapper: MultipartInstanceMapper<T>,
    ): MultipartInstanceProcessor<T> {
        return MultipartInstanceProcessor(
            integrationRequestProducerService = integrationRequestProducerService,
            instanceValidationService = instanceValidationService,
            receivedInstanceEventProducerService = receivedInstanceEventProducerService,
            instanceErrorEventProducerService = instanceErrorEventProducerService,
            sourceApplicationAuthorizationService = sourceApplicationAuthorizationService,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
            multipartInstanceMapper = multipartInstanceMapper,
        )
    }
}
