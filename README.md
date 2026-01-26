# FINT Flyt Web Instance Gateway

Spring Boot library for building FINT Flyt web-instance gateways. It validates and maps incoming instance payloads, checks integration status, uploads attached files to the file service, and publishes instance and error events to Kafka with consistent headers and error handling.

## Features
- Validates incoming payloads with Jakarta Bean Validation and emits mapped error events.
- Looks up integrations and enforces deactivated/unknown integration rules.
- Uploads files with OAuth2 client credentials and retry logic.
- Publishes instance-received and instance-receival-error events to Kafka.
- Provides auto-configuration for Kafka topic setup and the file-service RestClient.

## Installation
Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("no.novari:flyt-web-instance-gateway:<version>")
}
```

## Usage
Create a processor using the factory and delegate your controller to it.

```kotlin
@RestController
class InstanceController(
    processorFactory: InstanceProcessorFactoryService,
    private val mapper: InstanceMapper<IncomingInstance>,
) {
    private val processor =
        processorFactory.createInstanceProcessor(
            sourceApplicationIntegrationIdFunction = { it.integrationId },
            sourceApplicationInstanceIdFunction = { it.instanceId },
            instanceMapper = mapper,
        )

    @PostMapping("/instances")
    fun receive(
        authentication: Authentication,
        @RequestBody instance: IncomingInstance,
    ): ResponseEntity<Void> = processor.processInstance(authentication, instance)
}
```

Implement `InstanceMapper` to map your inbound model to an `InstanceObject`, and use the provided `persistFile` callback for attachments.

```kotlin
@Component
class IncomingInstanceMapper : InstanceMapper<IncomingInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: IncomingInstance,
        persistFile: (File) -> UUID,
    ): InstanceObject {
        val fileId =
            persistFile(
                File(
                    name = incomingInstance.fileName,
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationInstanceId = incomingInstance.instanceId,
                    type = MediaType.APPLICATION_PDF,
                    encoding = "base64",
                    base64Contents = incomingInstance.fileBase64,
                ),
            )

        return InstanceObject(
            valuePerKey =
                mapOf(
                    "instanceId" to incomingInstance.instanceId,
                    "fileId" to fileId.toString(),
                ),
        )
    }
}
```

If your integration id is fixed, you can use the overload that accepts a constant:

```kotlin
processorFactory.createInstanceProcessor(
    sourceApplicationIntegrationId = "integration-id",
    sourceApplicationInstanceIdFunction = { it.instanceId },
    instanceMapper = mapper,
)
```

## Configuration
Required:
- `novari.flyt.file-service-url` - base URL for the file service.

Optional:
- `novari.flyt.web-instance-gateway.check-integration-exists` (default `true`)
- `novari.flyt.web-instance-gateway.max-request-size` (default `100MB`)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.retention-time` (default `PT96H`)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.cleanup-frequency` (default `NORMAL`)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.partitions` (default `1`)

OAuth2 client setup (registration id must be `file-service`):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          file-service:
            client-id: your-client-id
            client-secret: your-client-secret
            authorization-grant-type: client_credentials
        provider:
          file-service:
            token-uri: https://auth.example.com/oauth/token
```
