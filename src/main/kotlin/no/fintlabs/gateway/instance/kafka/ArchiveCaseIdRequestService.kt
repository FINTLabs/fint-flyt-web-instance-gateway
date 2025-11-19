package no.fintlabs.gateway.instance.kafka

import no.fintlabs.gateway.instance.model.ArchiveCaseIdRequestParams
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
class ArchiveCaseIdRequestService(
    @Value("\${novari.kafka.application-id}") private val applicationId: String,
    @Value("\${novari.kafka.reply-topic.retention:1h}") private val replyRetention: Duration,
    @Value("\${novari.kafka.reply-topic.reply-timeout:10s}") private val replyTimeout: Duration,
    replyTopicService: ReplyTopicService,
    requestTemplateFactory: RequestTemplateFactory,
) {
    companion object {
        private const val TOPIC = "archive-instance-id"
        private const val PARAMETER_NAME = "source-application-instance-id"
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

    private var requestTemplate: RequestTemplate<ArchiveCaseIdRequestParams, String>

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
                ArchiveCaseIdRequestParams::class.java,
                String::class.java,
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

    fun getArchiveCaseId(
        sourceApplicationId: Long,
        sourceApplicationInstanceId: String,
    ): String? {
        return requestTemplate
            .requestAndReceive(
                RequestProducerRecord(
                    requestTopicNameParameters,
                    sourceApplicationInstanceId,
                    ArchiveCaseIdRequestParams(
                        sourceApplicationId = sourceApplicationId,
                        sourceApplicationInstanceId = sourceApplicationInstanceId,
                    ),
                ),
            ).value()
    }
}
