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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class IntegrationRequestProducerService(
    @Value("\${fint.application-id}") applicationId: String,
    requestProducerFactory: RequestProducerFactory,
    replyTopicService: ReplyTopicService,
) {
    companion object {
        private const val TOPIC: String = "integration"
        private const val PARAMETER_NAME: String = "source-application-id-and-source-application-integration-id"
        private val REPLY_TIMEOUT = 15.seconds
    }

    private lateinit var requestTopicNameParameters: RequestTopicNameParameters
    private lateinit var requestProducer:
        RequestProducer<SourceApplicationIdAndSourceApplicationIntegrationId, Integration>

    init {
        val replyTopicNameParameters =
            ReplyTopicNameParameters
                .builder()
                .applicationId(applicationId)
                .resource(TOPIC)
                .build()

        replyTopicService.ensureTopic(
            replyTopicNameParameters,
            0,
            TopicCleanupPolicyParameters.builder().build(),
        )

        requestTopicNameParameters =
            RequestTopicNameParameters
                .builder()
                .resource(TOPIC)
                .parameterName(PARAMETER_NAME)
                .build()

        requestProducer =
            requestProducerFactory.createProducer(
                replyTopicNameParameters,
                SourceApplicationIdAndSourceApplicationIntegrationId::class.java,
                Integration::class.java,
                RequestProducerConfiguration
                    .builder()
                    .defaultReplyTimeout(REPLY_TIMEOUT.toJavaDuration())
                    .build(),
            )
    }

    fun get(sourceAppIntegrationIds: SourceApplicationIdAndSourceApplicationIntegrationId): Integration? {
        return requestProducer
            .requestAndReceive(
                RequestProducerRecord
                    .builder<SourceApplicationIdAndSourceApplicationIntegrationId>()
                    .topicNameParameters(requestTopicNameParameters)
                    .value(sourceAppIntegrationIds)
                    .build(),
            ).orElse(null)
            ?.value()
    }
}
