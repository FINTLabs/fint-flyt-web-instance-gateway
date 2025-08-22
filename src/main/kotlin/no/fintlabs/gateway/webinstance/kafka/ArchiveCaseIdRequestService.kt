package no.fintlabs.gateway.webinstance.kafka

import no.fintlabs.gateway.webinstance.model.ArchiveCaseIdRequestParams
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class ArchiveCaseIdRequestService(
    @Value("\${fint.kafka.application-id}") applicationId: String,
    replyTopicService: ReplyTopicService,
    requestProducerFactory: RequestProducerFactory,
) {
    companion object {
        private const val TOPIC: String = "archive.instance.id"
        private const val PARAMETER_NAME: String = "source-application-instance-id"
        private val REPLY_TIMEOUT = 10.seconds
    }

    private val requestTopicNameParameters =
        RequestTopicNameParameters
            .builder()
            .resource(TOPIC)
            .parameterName(PARAMETER_NAME)
            .build()

    private lateinit var caseIdRequestProducer: RequestProducer<ArchiveCaseIdRequestParams, String>

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

        caseIdRequestProducer =
            requestProducerFactory.createProducer(
                replyTopicNameParameters,
                ArchiveCaseIdRequestParams::class.java,
                String::class.java,
                RequestProducerConfiguration
                    .builder()
                    .defaultReplyTimeout(REPLY_TIMEOUT.toJavaDuration())
                    .build(),
            )
    }

    fun getArchiveCaseId(
        sourceApplicationId: Long,
        sourceApplicationInstanceId: String,
    ): String? {
        return caseIdRequestProducer
            .requestAndReceive(
                RequestProducerRecord
                    .builder<ArchiveCaseIdRequestParams>()
                    .topicNameParameters(requestTopicNameParameters)
                    .value(
                        ArchiveCaseIdRequestParams(
                            sourceApplicationId = sourceApplicationId,
                            sourceApplicationInstanceId = sourceApplicationInstanceId,
                        ),
                    ).build(),
            ).orElse(null)
            ?.value()
    }
}
