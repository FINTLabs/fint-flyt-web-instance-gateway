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
import java.time.Duration
import java.util.*

@Service
class ArchiveCaseRequestService(
    @Value("\${fint.kafka.application-id}") applicationId: String,
    replyTopicService: ReplyTopicService,
    requestProducerFactory: RequestProducerFactory
) {

    private val topicName = "arkiv.noark.sak-with-filtered-journalposts"

    private val requestTopicNameParameters: RequestTopicNameParameters =
        RequestTopicNameParameters.builder()
            .resource(topicName)
            .parameterName("archive-instance-id")
            .build()

    private final val requestProducer: RequestProducer<String, SakResource>

    init {
        val replyTopicNameParameters = ReplyTopicNameParameters.builder()
            .applicationId(applicationId)
            .resource(topicName)
            .build()

        replyTopicService.ensureTopic(
            replyTopicNameParameters,
            0,
            TopicCleanupPolicyParameters.builder().build()
        )

        requestProducer = requestProducerFactory.createProducer(
            replyTopicNameParameters,
            String::class.java,
            SakResource::class.java,
            RequestProducerConfiguration.builder()
                .defaultReplyTimeout(Duration.ofSeconds(60))
                .build()
        )
    }

    fun getByArchiveCaseId(archiveCaseId: String): Optional<SakResource> {
        return requestProducer.requestAndReceive(
            RequestProducerRecord.builder<String>()
                .topicNameParameters(requestTopicNameParameters)
                .value(archiveCaseId)
                .build()
        ).map { it.value() }
    }

}