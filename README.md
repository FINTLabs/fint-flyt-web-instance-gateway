# FINT Flyt Web Instance Gateway

Spring Boot library for building FINT Flyt web-instance gateways. It validates and maps incoming instance payloads, checks integration status, uploads attached files to the file service, and publishes instance and error events to Kafka with consistent headers and error handling.

## Features
- Validates incoming payloads with Jakarta Bean Validation and emits mapped error events.
- Looks up integrations in intgr-conf-service over HTTP and enforces deactivated/unknown integration rules.
- `HistoryClient` looks up the archive instance ID of an instance in history-service over HTTP.
- Uploads files with OAuth2 client credentials and retry logic.
- Publishes instance-received and instance-receival-error events to Kafka.
- Provides autoconfiguration for Kafka topic setup and the RestClients for file-service, intgr-conf-service and history-service.

Architecture (C4 level 1 to 3): [docs/architecture.md](docs/architecture.md).

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
    mapper: InstanceMapper<IncomingInstance>,
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

## Multipart usage
Existing JSON/Base64 integrations can keep using `InstanceProcessor` and `InstanceMapper`.
New integrations can use `MultipartInstanceProcessor` and send metadata as JSON in one part and binary files in separate parts.

```kotlin
@RestController
class MultipartInstanceController(
    processorFactory: InstanceProcessorFactoryService,
    mapper: MultipartInstanceMapper<IncomingInstance>,
) {
    private val processor =
        processorFactory.createMultipartInstanceProcessor(
            sourceApplicationIntegrationIdFunction = { it.integrationId },
            sourceApplicationInstanceIdFunction = { it.instanceId },
            multipartInstanceMapper = mapper,
        )

    @PostMapping("/instances", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun receive(
        authentication: Authentication,
        @RequestPart("instance") instance: IncomingInstance,
        @RequestPart("files") files: List<MultipartFile>,
    ): ResponseEntity<Void> = processor.processInstance(authentication, instance, files)
}
```

The multipart mapper links document metadata to the correct file part by returning a `MultipartFileReference`.
Use `originalFilename` when multiple uploaded files share the same part name.

```kotlin
@Component
class IncomingMultipartInstanceMapper : MultipartInstanceMapper<IncomingInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: IncomingInstance,
        persistFile: (MultipartFileReference) -> UUID,
    ): InstanceObject {
        val fileId =
            persistFile(
                MultipartFileReference(
                    partName = incomingInstance.filePartName,
                    fileName = incomingInstance.fileName,
                    originalFilename = incomingInstance.originalFilename,
                    type = MediaType.APPLICATION_PDF,
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

## Configuration
Required:
- `novari.flyt.file-service-url` - base URL for the file service.

Optional:
- `novari.flyt.web-instance-gateway.check-integration-exists` (default `true`)
- `novari.flyt.web-instance-gateway.max-request-size` (default `100MB`)
- `novari.flyt.web-instance-gateway.jackson.max-string-length` (default: same as `max-request-size`; override with values like `120MB` or bytes)
- `spring.servlet.multipart.max-file-size` (default: `novari.flyt.web-instance-gateway.max-request-size` unless explicitly set)
- `spring.servlet.multipart.max-request-size` (default: `novari.flyt.web-instance-gateway.max-request-size` unless explicitly set)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.retention-time` (default `PT96H`)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.cleanup-frequency` (default `NORMAL`)
- `novari.flyt.web-instance-gateway.kafka.topic.instance-receival-error.partitions` (default `1`)

- `novari.flyt.intgr-conf-service-url` (default `http://fint-flyt-intgr-conf-service:8080${server.servlet.context-path:}`)
- `novari.flyt.history-service-url` (default `http://fint-flyt-history-service:8080${server.servlet.context-path:}`)
- `spring.http.client.connect-timeout` (default `2s`) and `spring.http.client.read-timeout` (default `5s`) for intgr-conf-service, history-service and the token request to NAM. The defaults apply only to these.

The URL defaults assume the app runs with the same context path as the two services, which is the case in all FLYT overlays.

### OAuth clients

The app needs two OAuth clients (`NamOAuthClientApplicationResource`, `client_credentials`) and four registrations:

| Registration id         | OAuth client                            | Used for                                                                 |
|-------------------------|-----------------------------------------|--------------------------------------------------------------------------|
| `file-service`          | `fint-flyt-file-oauth2-client`          | Uploading files                                                          |
| `intgr-conf-service`    | `fint-flyt-authorization-oauth2-client` | `GET /api/intern-klient/integrasjoner`                                   |
| `history-service`       | `fint-flyt-authorization-oauth2-client` | `GET /api/intern-klient/archive-instance-id`                             |
| `authorization-service` | `fint-flyt-authorization-oauth2-client` | Source application authorization in flyt-web-resource-server (`/api/**`) |

The registration ids are fixed. A 404 from intgr-conf-service or history-service is returned as `null`. Other errors are
thrown. See the [flyt-web-resource-server README](https://github.com/FINTLabs/fint-flyt-web-resource-server) for
`authorization-service`.

`fint-flyt-authorization-oauth2-client` is the shared OAuth client for internal calls between FLYT services, because
namerator does not create new NAM clients. The registrations stay separate per target service, so each can get its own
client later by changing configuration only.

Every NAM secret has the keys `fint.sso.client-id` and `fint.sso.client-secret`. Load the file-service client with
`envFrom` and map the shared client to its own property names with `secretKeyRef`, so the values do not collide:

```yaml
envFrom:
  - secretRef:
      name: fint-flyt-file-oauth2-client
env:
  - name: fint.flyt.authorization.sso.client-id
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-id
  - name: fint.flyt.authorization.sso.client-secret
    valueFrom:
      secretKeyRef:
        name: fint-flyt-authorization-oauth2-client
        key: fint.sso.client-secret
```

In `ra-no` the shared client comes from 1Password with the keys `fint.flyt.authorization.sso.client-id` and
`fint.flyt.authorization.sso.client-secret`, so the overlay must patch `secretKeyRef.key`.

Do not use `clientIdProperty` on the NAM resource. The services declare the same resource and read `fint.sso.client-id`
from it.

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          fint-idp:
            token-uri: https://idp.felleskomponent.no/nidp/oauth/nam/token?resourceServer=fint-api
        registration:
          file-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.sso.client-id}
            client-secret: ${fint.sso.client-secret}
          intgr-conf-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
          history-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
          authorization-service:
            authorization-grant-type: client_credentials
            provider: fint-idp
            client-id: ${fint.flyt.authorization.sso.client-id}
            client-secret: ${fint.flyt.authorization.sso.client-secret}
```

### Upgrading from 3.x

- `IntegrationRequestProducerService` and `ArchiveCaseIdRequestService` are removed. Use `IntgrConfClient` and `HistoryClient.getArchiveInstanceId(sourceApplicationId, sourceApplicationInstanceId)`.
- Add `fint-flyt-authorization-oauth2-client` and the registrations above. flyt-web-resource-server 5 needs
  `authorization-service`, or the gateway fails at startup. See its README for the other changes in 5.x.
- The gateway no longer uses Kafka request/reply, so `novari.kafka.reply-topic.*` has no effect.

## Dependency Locking

The build uses [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html), so every build resolves exactly the same direct and transitive versions. The locked versions live in the committed `gradle.lockfile` files. A pull request therefore shows every transitive version change it causes. Gradle plugins are not locked, because Dependabot only updates `gradle.lockfile`; their versions are pinned in the `plugins` block instead.

**Automatic (Dependabot):** Dependabot updates the lockfiles in its own pull requests, so no manual step is needed. If two Dependabot pull requests conflict in a lockfile, Dependabot rebases the remaining one after the first is merged. If it does not, comment `@dependabot recreate` on the pull request.

**Manual:** Any change you make by hand that affects dependency resolution requires regenerating the lockfiles. That covers versions, `extra[...]` properties, constraints, and adding or removing a dependency:

```bash
./gradlew dependencies --write-locks
```

Commit the updated lockfiles together with the change. If you forget, the build fails both locally and in CI with an error saying the resolved version "is not part of the dependency lock state".
