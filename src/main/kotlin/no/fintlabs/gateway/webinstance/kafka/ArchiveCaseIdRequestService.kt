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
import java.time.Duration
import java.util.*

@Service
class ArchiveCaseIdRequestService(
    @Value("\${fint.kafka.application-id}") applicationId: String,
    replyTopicService: ReplyTopicService,
    requestProducerFactory: RequestProducerFactory
) {

    private val topicName = "archive.instance.id"

    private val requestTopicNameParameters: RequestTopicNameParameters =
        RequestTopicNameParameters.builder()
            .resource(topicName)
            .parameterName("source-application-instance-id")
            .build()

    private final val caseIdRequestProducer: RequestProducer<ArchiveCaseIdRequestParams, String>

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

        caseIdRequestProducer = requestProducerFactory.createProducer(
            replyTopicNameParameters,
            ArchiveCaseIdRequestParams::class.java,
            String::class.java,
            RequestProducerConfiguration.builder()
                .defaultReplyTimeout(Duration.ofSeconds(10))
                .build()
        )
    }

    fun getArchiveCaseId(
        sourceApplicationId: Long,
        sourceApplicationInstanceId: String
    ): Optional<String> {
        return caseIdRequestProducer.requestAndReceive(
            RequestProducerRecord.builder<ArchiveCaseIdRequestParams>()
                .topicNameParameters(requestTopicNameParameters)
                .value(
                    ArchiveCaseIdRequestParams(
                        sourceApplicationId = sourceApplicationId,
                        sourceApplicationInstanceId = sourceApplicationInstanceId
                    )
                )
                .build()
        ).map { consumerRecord -> consumerRecord.value() }
    }

}
