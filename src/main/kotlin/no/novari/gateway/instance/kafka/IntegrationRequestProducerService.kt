package no.novari.gateway.instance.kafka

import no.novari.gateway.instance.model.Integration
import no.novari.gateway.instance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.novari.kafka.consuming.ListenerConfiguration
import no.novari.kafka.requestreply.RequestProducerRecord
import no.novari.kafka.requestreply.RequestTemplate
import no.novari.kafka.requestreply.RequestTemplateFactory
import no.novari.kafka.requestreply.topic.ReplyTopicService
import no.novari.kafka.requestreply.topic.configuration.ReplyTopicConfiguration
import no.novari.kafka.requestreply.topic.name.ReplyTopicNameParameters
import no.novari.kafka.requestreply.topic.name.RequestTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class IntegrationRequestProducerService(
    @param:Value("\${fint.application-id}") private val applicationId: String,
    @param:Value("\${novari.kafka.reply-topic.retention:1h}") private val replyRetention: Duration,
    @param:Value("\${novari.kafka.reply-topic.reply-timeout:15s}") private val replyTimeout: Duration,
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
            .topicNamePrefixParameters(
                TopicNamePrefixParameters
                    .stepBuilder()
                    .orgIdApplicationDefault()
                    .domainContextApplicationDefault()
                    .build(),
            ).resourceName(TOPIC)
            .parameterName(PARAMETER_NAME)
            .build()

    private val requestTemplate: RequestTemplate<SourceApplicationIdAndSourceApplicationIntegrationId, Integration>

    init {
        val replyTopicNameParameters =
            ReplyTopicNameParameters
                .builder()
                .topicNamePrefixParameters(
                    TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build(),
                ).applicationId(applicationId)
                .resourceName(TOPIC)
                .build()

        replyTopicService.createOrModifyTopic(
            replyTopicNameParameters,
            ReplyTopicConfiguration
                .builder()
                .retentionTime(replyRetention)
                .build(),
        )

        requestTemplate =
            requestTemplateFactory.createTemplate(
                replyTopicNameParameters,
                SourceApplicationIdAndSourceApplicationIntegrationId::class.java,
                Integration::class.java,
                replyTimeout,
                ListenerConfiguration
                    .stepBuilder()
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .continueFromPreviousOffsetOnAssignment()
                    .build(),
            )
    }

    fun get(params: SourceApplicationIdAndSourceApplicationIntegrationId): Integration? {
        return requestTemplate
            .requestAndReceive(
                RequestProducerRecord(
                    requestTopicNameParameters,
                    null,
                    params,
                ),
            ).value()
    }
}
