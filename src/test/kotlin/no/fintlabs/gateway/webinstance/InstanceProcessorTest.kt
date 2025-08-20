package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.exception.FileUploadException
import no.fintlabs.gateway.webinstance.kafka.InstanceReceivalErrorEventProducerService
import no.fintlabs.gateway.webinstance.kafka.IntegrationRequestProducerService
import no.fintlabs.gateway.webinstance.kafka.ReceivedInstanceEventProducerService
import no.fintlabs.gateway.webinstance.model.File
import no.fintlabs.gateway.webinstance.model.Integration
import no.fintlabs.gateway.webinstance.model.instance.InstanceObject
import no.fintlabs.gateway.webinstance.validation.InstanceValidationService
import no.fintlabs.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class InstanceProcessorTest {
    @Mock
    private lateinit var integrationRequestProducerService: IntegrationRequestProducerService

    @Mock
    private lateinit var instanceValidationService: InstanceValidationService

    @Mock
    private lateinit var receivedInstanceEventProducerService: ReceivedInstanceEventProducerService

    @Mock
    private lateinit var instanceReceivalErrorEventProducerService: InstanceReceivalErrorEventProducerService

    @Mock
    private lateinit var sourceApplicationAuthorizationService: SourceApplicationAuthorizationService

    @Mock
    private lateinit var fileClient: FileClient

    @Mock
    private lateinit var sourceApplicationIntegrationIdFunction: (Any) -> String?

    @Mock
    private lateinit var sourceApplicationInstanceIdFunction: (Any) -> String?

    @Mock
    private lateinit var instanceMapper: InstanceMapper<Any>

    @Mock
    private lateinit var instanceObject: InstanceObject

    @InjectMocks
    private lateinit var instanceProcessor: InstanceProcessor<Any>

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
    }

    @Test
    fun shouldProcessInstanceAndPostFileSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result = instanceProcessor.processInstance(authentication, incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(fileClient, times(1)).postFile(any())
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), any())
    }

    @Test
    fun shouldProcessInstanceAndPostMultipleFilesSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result = instanceProcessor.processInstance(authentication, incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(fileClient, times(3)).postFile(any())
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), eq(instanceObject))
    }

    @Test
    fun shouldProcessInstanceAndHandleSecondFileFailure() {
        val mockFile = mock<File>()
        val fileUploadException = FileUploadException(mockFile, "File upload failed")

        whenever(fileClient.postFile(any())).thenReturn(UUID.randomUUID()).thenThrow(fileUploadException)

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result = instanceProcessor.processInstance(authentication, incoming)

        assertTrue(result.statusCode.is5xxServerError)
        assertEquals(500, result.statusCode.value())

        val body = result.body ?: fail("Response body is null")
        assertTrue(body.toString().contains("File upload failed"))
    }
}
