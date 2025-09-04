package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.gateway.webinstance.model.Integration
import no.fintlabs.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.fintlabs.kafka.consuming.ErrorHandlerConfiguration
import no.fintlabs.kafka.consuming.ListenerConfiguration
import no.fintlabs.kafka.requestreply.RequestProducerRecord
import no.fintlabs.kafka.requestreply.RequestTemplate
import no.fintlabs.kafka.requestreply.RequestTemplateFactory
import no.fintlabs.kafka.requestreply.topic.ReplyTopicService
import no.fintlabs.kafka.requestreply.topic.configuration.ReplyTopicConfiguration
import no.fintlabs.kafka.requestreply.topic.name.ReplyTopicNameParameters
import no.fintlabs.kafka.requestreply.topic.name.RequestTopicNameParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class IntegrationRequestProducerService(
    @Value("\${fint.kafka.application-id}") private val applicationId: String,
    @Value("\${fint.kafka.reply-topic.retention-hours:1}") private val replyRetentionHours: Long,
    @Value("\${fint.kafka.reply-topic.reply-timeout-seconds:15}") private val replyTimeoutSeconds: Long,
    replyTopicService: ReplyTopicService,
    requestTemplateFactory: RequestTemplateFactory,
) {
    companion object {
        private const val TOPIC = "integration"
        private const val PARAMETER_NAME = "source-application-id-and-source-application-integration-id"
    }

    private val requestTopicNameParameters =
        RequestTopicNameParameters
            .builder()
            .resourceName(TOPIC)
            .parameterName(PARAMETER_NAME)
            .build()

    private val requestTemplate: RequestTemplate<SourceApplicationIdAndSourceApplicationIntegrationId, Integration>

    init {
        val replyTopicNameParameters =
            ReplyTopicNameParameters
                .builder()
                .applicationId(applicationId)
                .resourceName(TOPIC)
                .build()

        replyTopicService.createOrModifyTopic(
            replyTopicNameParameters,
            ReplyTopicConfiguration
                .builder()
                .retentionTime(replyRetentionHours.hours.toJavaDuration())
                .build(),
        )

        requestTemplate =
            requestTemplateFactory.createTemplate(
                replyTopicNameParameters,
                SourceApplicationIdAndSourceApplicationIntegrationId::class.java,
                replyTimeoutSeconds.seconds.toJavaDuration(),
                ListenerConfiguration
                    .builder(Integration::class.java)
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .errorHandler(
                        ErrorHandlerConfiguration
                            .builder(Integration::class.java)
                            .noRetries()
                            .skipFailedRecords()
                            .build(),
                    ).continueFromPreviousOffsetOnAssignment()
                    .build(),
            )
    }

    fun get(params: SourceApplicationIdAndSourceApplicationIntegrationId): Integration? {
        return requestTemplate
            .requestAndReceive(
                RequestProducerRecord(
                    requestTopicNameParameters,
                    // TODO: Is this a proper key?
                    params.sourceApplicationIntegrationId,
                    params,
                ),
            ).value()
    }
}
