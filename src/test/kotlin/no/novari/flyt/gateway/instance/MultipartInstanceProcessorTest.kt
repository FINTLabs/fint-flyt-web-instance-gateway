package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.webinstance.FileClient
import no.novari.flyt.gateway.webinstance.MultipartInstanceMapper
import no.novari.flyt.gateway.webinstance.MultipartInstanceProcessor
import no.novari.flyt.gateway.webinstance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.novari.flyt.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.webinstance.model.Integration
import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import no.novari.flyt.gateway.webinstance.model.MultipartFileUpload
import no.novari.flyt.gateway.webinstance.model.instance.InstanceObject
import no.novari.flyt.gateway.webinstance.validation.InstanceValidationService
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

class MultipartInstanceProcessorTest {
    @Mock
    private lateinit var integrationRequestProducerService: IntegrationRequestProducerService

    @Mock
    private lateinit var instanceValidationService: InstanceValidationService

    @Mock
    private lateinit var receivedInstanceEventProducerService: ReceivedInstanceEventProducerService

    @Mock
    private lateinit var instanceErrorEventProducerService: InstanceErrorEventProducerService

    @Mock
    private lateinit var sourceApplicationAuthorizationService: SourceApplicationAuthorizationService

    @Mock
    private lateinit var fileClient: FileClient

    @Mock
    private lateinit var sourceApplicationIntegrationIdFunction: (Any) -> String

    @Mock
    private lateinit var sourceApplicationInstanceIdFunction: (Any) -> String

    @Mock
    private lateinit var multipartInstanceMapper: MultipartInstanceMapper<Any>

    @Mock
    private lateinit var instanceObject: InstanceObject

    private lateinit var multipartInstanceProcessor: MultipartInstanceProcessor<Any>

    private lateinit var authentication: Authentication

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        authentication = mock()

        val mockIntegration = mock<Integration>()
        whenever(mockIntegration.id).thenReturn(1L)
        whenever(mockIntegration.state).thenReturn(Integration.State.ACTIVE)
        whenever(integrationRequestProducerService.get(any()))
            .thenReturn(mockIntegration)

        whenever(sourceApplicationAuthorizationService.getSourceApplicationId(any()))
            .thenReturn(123L)

        whenever(sourceApplicationIntegrationIdFunction.invoke(any()))
            .thenReturn("integrationId")
        whenever(sourceApplicationInstanceIdFunction.invoke(any()))
            .thenReturn("instanceId")

        whenever(instanceValidationService.validate(any())).thenReturn(null)

        multipartInstanceProcessor =
            MultipartInstanceProcessor(
                integrationRequestProducerService = integrationRequestProducerService,
                instanceValidationService = instanceValidationService,
                receivedInstanceEventProducerService = receivedInstanceEventProducerService,
                instanceErrorEventProducerService = instanceErrorEventProducerService,
                sourceApplicationAuthorizationService = sourceApplicationAuthorizationService,
                fileClient = fileClient,
                sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
                sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
                multipartInstanceMapper = multipartInstanceMapper,
            )
    }

    @Test
    fun shouldProcessMultipartInstanceAndPostFileSuccessfully() {
        val fileId = UUID.randomUUID()
        whenever(fileClient.postFile(any<MultipartFileUpload>())).thenReturn(fileId)

        whenever(multipartInstanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val persistFile = invocation.getArgument<(MultipartFileReference) -> UUID>(2)
            val persistedFileId =
                persistFile(
                    MultipartFileReference(
                        partName = "document",
                        fileName = "document.pdf",
                        type = MediaType.APPLICATION_PDF,
                    ),
                )
            assertEquals(fileId, persistedFileId)
            instanceObject
        }

        val incoming = Any()
        val multipartFile =
            MockMultipartFile(
                "document",
                "document.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test".toByteArray(),
            )

        val result = multipartInstanceProcessor.processInstance(authentication, incoming, listOf(multipartFile))

        assertEquals(ResponseEntity.accepted().build(), result)
        val fileUploadCaptor = argumentCaptor<MultipartFileUpload>()
        verify(fileClient, times(1)).postFile(fileUploadCaptor.capture())
        assertThat(fileUploadCaptor.firstValue.name).isEqualTo("document.pdf")
        assertThat(fileUploadCaptor.firstValue.sourceApplicationId).isEqualTo(123L)
        assertThat(fileUploadCaptor.firstValue.sourceApplicationInstanceId).isEqualTo("instanceId")
        assertThat(fileUploadCaptor.firstValue.type).isEqualTo(MediaType.APPLICATION_PDF)
        assertThat(fileUploadCaptor.firstValue.multipartFile).isSameAs(multipartFile)
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), eq(instanceObject))
    }

    @Test
    fun shouldLinkMetadataToCorrectFilePartWhenPartNameIsShared() {
        val fileId = UUID.randomUUID()
        whenever(fileClient.postFile(any<MultipartFileUpload>())).thenReturn(fileId)

        whenever(multipartInstanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val persistFile = invocation.getArgument<(MultipartFileReference) -> UUID>(2)
            persistFile(
                MultipartFileReference(
                    partName = "documents",
                    fileName = "target.pdf",
                    originalFilename = "target-upload.pdf",
                    type = MediaType.APPLICATION_PDF,
                ),
            )
            instanceObject
        }

        val otherFile =
            MockMultipartFile(
                "documents",
                "other-upload.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "other".toByteArray(),
            )
        val targetFile =
            MockMultipartFile(
                "documents",
                "target-upload.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "target".toByteArray(),
            )

        multipartInstanceProcessor.processInstance(authentication, Any(), listOf(otherFile, targetFile))

        val fileUploadCaptor = argumentCaptor<MultipartFileUpload>()
        verify(fileClient).postFile(fileUploadCaptor.capture())
        assertThat(fileUploadCaptor.firstValue.name).isEqualTo("target.pdf")
        assertThat(fileUploadCaptor.firstValue.multipartFile).isSameAs(targetFile)
    }

    @Test
    fun shouldPublishRejectedEventWhenMultipartFilePartIsMissing() {
        whenever(multipartInstanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val persistFile = invocation.getArgument<(MultipartFileReference) -> UUID>(2)
            persistFile(MultipartFileReference(partName = "missing-file"))
            instanceObject
        }

        val exception =
            assertThrows<ResponseStatusException> {
                multipartInstanceProcessor.processInstance(authentication, Any(), emptyList())
            }

        assertThat(exception.statusCode.value()).isEqualTo(422)
        assertThat(exception).hasMessageContaining("No multipart file part found")
        assertThat(exception).hasMessageNotContaining("missing-file")
        verify(instanceErrorEventProducerService, times(1)).publishInstanceRejectedErrorEvent(any(), any())
        verify(fileClient, never()).postFile(any<MultipartFileUpload>())
        verify(receivedInstanceEventProducerService, never()).publish(any(), any())
    }
}
