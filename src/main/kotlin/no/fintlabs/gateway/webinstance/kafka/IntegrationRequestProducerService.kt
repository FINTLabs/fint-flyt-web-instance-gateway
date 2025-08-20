package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.gateway.webinstance.model.Integration
import no.fintlabs.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters
import no.fintlabs.kafka.requestreply.RequestProducer
import no.fintlabs.kafka.requestreply.RequestProducerConfiguration
import no.fintlabs.kafka.requestreply.RequestProducerFactory
import no.fintlabs.kafka.requestreply.RequestProducerRecord
import no.fintlabs.kafka.requestreply.topic.ReplyTopicNameParameters
import no.fintlabs.kafka.requestreply.topic.ReplyTopicService
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Optional

@Service
class IntegrationRequestProducerService(
    @Value("\${fint.application-id}") applicationId: String,
    requestProducerFactory: RequestProducerFactory,
    replyTopicService: ReplyTopicService,
) {
    private val topicName: String = "integration"
    private final val requestTopicNameParameters: RequestTopicNameParameters
    private final val requestProducer:
        RequestProducer<SourceApplicationIdAndSourceApplicationIntegrationId, Integration>

    init {
        val replyTopicNameParameters =
            ReplyTopicNameParameters
                .builder()
                .applicationId(applicationId)
                .resource(topicName)
                .build()

        replyTopicService.ensureTopic(
            replyTopicNameParameters,
            0,
            TopicCleanupPolicyParameters.builder().build(),
        )

        requestTopicNameParameters =
            RequestTopicNameParameters
                .builder()
                .resource(topicName)
                .parameterName("source-application-id-and-source-application-integration-id")
                .build()

        requestProducer =
            requestProducerFactory.createProducer(
                replyTopicNameParameters,
                SourceApplicationIdAndSourceApplicationIntegrationId::class.java,
                Integration::class.java,
                RequestProducerConfiguration
                    .builder()
                    .defaultReplyTimeout(Duration.ofSeconds(15))
                    .build(),
            )
    }

    fun get(sourceAppIntegrationIds: SourceApplicationIdAndSourceApplicationIntegrationId): Optional<Integration> {
        return requestProducer
            .requestAndReceive(
                RequestProducerRecord
                    .builder<SourceApplicationIdAndSourceApplicationIntegrationId>()
                    .topicNameParameters(requestTopicNameParameters)
                    .value(sourceAppIntegrationIds)
                    .build(),
            ).map { record -> record.value() }
    }
}
