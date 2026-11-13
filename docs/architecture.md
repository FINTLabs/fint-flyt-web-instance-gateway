# Architecture

C4 level 1 to 3 for flyt-web-instance-gateway. It is a library, so the diagrams show it inside the gateway that uses it, fint-flyt-vigo-gateway. Blue is this library, turquoise is external.

## C4 level 1: System context

A source application sends an instance. The gateway checks the integration, stores the files and hands the instance over to history-service.

```mermaid
C4Context
    title System context for a web-instance gateway

    System_Ext(source, "Source application", "For example VIGO.")
    System_Ext(idp, "NAM", "Issues OAuth2 tokens.")
    System(gateway, "Web-instance gateway", "fint-flyt-vigo-gateway on flyt-web-instance-gateway.")
    System_Ext(intgrConf, "fint-flyt-intgr-conf-service", "Integrations.")
    System_Ext(file, "fint-flyt-file-service", "Stores files.")
    System_Ext(history, "fint-flyt-history-service", "Registers instances and records events.")

    Rel(source, gateway, "Instances, status", "HTTPS")
    Rel(gateway, idp, "Fetches token", "client_credentials")
    Rel(gateway, intgrConf, "Integration", "HTTP")
    Rel(gateway, file, "Files", "HTTP")
    Rel(gateway, history, "Events, status", "Kafka, HTTP")

    UpdateRelStyle(source, gateway, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(gateway, idp, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(gateway, intgrConf, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(gateway, file, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(gateway, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateElementStyle(source, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(idp, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(gateway, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(intgrConf, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(file, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(history, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

## C4 level 2: Containers

One Spring Boot application with no database. Every HTTP call to an internal API uses its own registration and a token from NAM: `file-service`, `intgr-conf-service` and `history-service`. The registrations use two OAuth clients: `fint-flyt-file-oauth2-client` for file-service, and the shared `fint-flyt-authorization-oauth2-client` for intgr-conf-service and history-service. flyt-web-resource-server uses the shared client with the registration `authorization-service` to authorize source applications.

```mermaid
C4Container
    title Containers

    System_Ext(source, "Source application", "Authenticates with a NAM token.")
    Container_Ext(intgrConf, "fint-flyt-intgr-conf-service", "Spring Boot", "/api/intern-klient")
    Container(app, "fint-flyt-vigo-gateway", "Spring Boot 3, Java 25", "Validates, maps and publishes instances. No database.")
    Container_Ext(file, "fint-flyt-file-service", "Spring Boot", "/api/intern-klient")
    ContainerQueue_Ext(kafka, "Kafka", "Topic", "instance-received, instance-error")
    Container_Ext(history, "fint-flyt-history-service", "Spring Boot", "/api/intern-klient")

    Rel(source, app, "POST, GET", "HTTPS")
    Rel(app, intgrConf, "Integration", "HTTP")
    Rel(app, file, "Files", "HTTP")
    Rel(app, kafka, "Publishes")
    Rel(app, history, "Status", "HTTP")
    Rel(history, kafka, "Consumes")

    UpdateRelStyle(source, app, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, intgrConf, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(app, file, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(app, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(app, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(history, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateElementStyle(source, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(intgrConf, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(app, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(file, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(history, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

## C4 level 3: Components

The controller and the mapper are gateway code. Everything else comes from the library.

```mermaid
C4Component
    title Components in a web-instance gateway

    Component(historyClient, "HistoryClient", "RestClient", "404 gives null.")
    Container_Ext(history, "fint-flyt-history-service", "Spring Boot", "/api/intern-klient")
    Component(controller, "Controller", "Gateway code", "POST instance and GET status.")
    Container_Ext(intgrConf, "fint-flyt-intgr-conf-service", "Spring Boot", "/api/intern-klient")
    Component(processor, "InstanceProcessor", "Library", "Authorizes, validates, checks the integration, maps and publishes.")
    Component(intgrConfClient, "IntgrConfClient", "RestClient", "404 gives null, which rejects the instance.")
    Component(mapper, "InstanceMapper", "Gateway code", "Maps to an InstanceObject. Calls persistFile for attachments.")
    Component(producers, "Kafka producers", "flyt-kafka", "instance-received and instance-error.")
    Component(fileClient, "FileClient", "RestClient", "Retries up to 5 times.")
    ContainerQueue_Ext(kafka, "Kafka", "Topic", "instance-received, instance-error")
    Container_Ext(file, "fint-flyt-file-service", "Spring Boot", "/api/intern-klient")

    Rel(controller, historyClient, "Status")
    Rel(historyClient, history, "GET", "HTTP")
    Rel(controller, processor, "processInstance()")
    Rel(processor, intgrConfClient, "Integration")
    Rel(intgrConfClient, intgrConf, "GET", "HTTP")
    Rel(processor, mapper, "Maps")
    Rel(processor, producers, "Publishes")
    Rel(mapper, fileClient, "persistFile")
    Rel(producers, kafka, "Sends")
    Rel(fileClient, file, "POST", "HTTP")

    UpdateRelStyle(controller, historyClient, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(historyClient, history, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(controller, processor, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(processor, intgrConfClient, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="-35", $offsetY="-30")
    UpdateRelStyle(intgrConfClient, intgrConf, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(processor, mapper, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(processor, producers, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="20")
    UpdateRelStyle(mapper, fileClient, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(producers, kafka, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateRelStyle(fileClient, file, $textColor="#3B82F6", $lineColor="#3B82F6", $offsetX="10")
    UpdateElementStyle(historyClient, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(history, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(controller, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(intgrConf, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(processor, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(intgrConfClient, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(mapper, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(producers, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(fileClient, $bgColor="#93C5FD", $fontColor="#1F2937", $borderColor="#3B82F6")
    UpdateElementStyle(kafka, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateElementStyle(file, $bgColor="#A5F3FC", $fontColor="#1F2937", $borderColor="#06B6D4")
    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

A rejected instance (validation error, unknown or deactivated integration) gives 422, and other errors give 500. Both are published as `instance-error`. vigo-gateway answers 400 to a status request for an instance that history-service does not know.
