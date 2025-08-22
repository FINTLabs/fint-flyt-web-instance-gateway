package no.fintlabs.gateway.webinstance.kafka

import no.fint.model.resource.arkiv.noark.SakResource
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
class ArchiveCaseRequestService(
    @Value("\${fint.kafka.application-id}") applicationId: String,
    replyTopicService: ReplyTopicService,
    requestProducerFactory: RequestProducerFactory,
) {
    companion object {
        private const val TOPIC: String = "arkiv.noark.sak-with-filtered-journalposts"
        private const val PARAMETER_NAME: String = "archive-instance-id"
        private val REPLY_TIMEOUT = 60.seconds
    }

    private val requestTopicNameParameters =
        RequestTopicNameParameters
            .builder()
            .resource(TOPIC)
            .parameterName(PARAMETER_NAME)
            .build()

    private lateinit var requestProducer: RequestProducer<String, SakResource>

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

        requestProducer =
            requestProducerFactory.createProducer(
                replyTopicNameParameters,
                String::class.java,
                SakResource::class.java,
                RequestProducerConfiguration
                    .builder()
                    .defaultReplyTimeout(REPLY_TIMEOUT.toJavaDuration())
                    .build(),
            )
    }

    fun getByArchiveCaseId(archiveCaseId: String): SakResource? {
        return requestProducer
            .requestAndReceive(
                RequestProducerRecord
                    .builder<String>()
                    .topicNameParameters(requestTopicNameParameters)
                    .value(archiveCaseId)
                    .build(),
            ).orElse(null)
            ?.value()
    }
}
