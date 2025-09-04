package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.gateway.webinstance.model.ArchiveCaseIdRequestParams
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
class ArchiveCaseIdRequestService(
    @Value("\${fint.kafka.application-id}") private val applicationId: String,
    @Value("\${fint.kafka.reply-topic.retention-hours:1}") private val replyRetentionHours: Long,
    @Value("\${fint.kafka.reply-topic.reply-timeout-seconds:10}") private val replyTimeoutSeconds: Long,
    replyTopicService: ReplyTopicService,
    requestTemplateFactory: RequestTemplateFactory,
) {
    companion object {
        private const val TOPIC = "archive.instance.id"
        private const val PARAMETER_NAME = "source-application-instance-id"
    }

    private val requestTopicNameParameters =
        RequestTopicNameParameters
            .builder()
            .resourceName(TOPIC)
            .parameterName(PARAMETER_NAME)
            .build()

    private var requestTemplate: RequestTemplate<ArchiveCaseIdRequestParams, String>

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
                ArchiveCaseIdRequestParams::class.java,
                replyTimeoutSeconds.seconds.toJavaDuration(),
                ListenerConfiguration
                    .builder(String::class.java)
                    .groupIdApplicationDefault()
                    .maxPollRecordsKafkaDefault()
                    .maxPollIntervalKafkaDefault()
                    .errorHandler(
                        ErrorHandlerConfiguration
                            .builder(String::class.java)
                            .noRetries()
                            .skipFailedRecords()
                            .build(),
                    ).continueFromPreviousOffsetOnAssignment()
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
