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
import org.mockito.kotlin.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.util.*
import java.util.function.Function
import kotlin.test.assertEquals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException

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
    private lateinit var sourceApplicationIntegrationIdFunction: Function<Any, Optional<String>>

    @Mock
    private lateinit var sourceApplicationInstanceIdFunction: Function<Any, Optional<String>>

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
            .thenReturn(Optional.of(mockIntegration))

        whenever(sourceApplicationAuthorizationService.getSourceApplicationId(any()))
            .thenReturn(123L)

        whenever(sourceApplicationIntegrationIdFunction.apply(any()))
            .thenReturn(Optional.of("integrationId"))
        whenever(sourceApplicationInstanceIdFunction.apply(any()))
            .thenReturn(Optional.of("instanceId"))
    }

    @Test
    fun shouldProcessInstanceAndPostFileSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<Function<File, UUID>>(2)
            postFile.apply(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result: ResponseEntity<Void> = instanceProcessor.processInstance(authentication, incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(fileClient, times(1)).postFile(any())
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), any())
    }

    @Test
    fun shouldProcessInstanceAndPostMultipleFilesSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<Function<File, UUID>>(2)
            postFile.apply(mockFile)
            postFile.apply(mockFile)
            postFile.apply(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result: ResponseEntity<Void> = instanceProcessor.processInstance(authentication, incoming)

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
            val postFile = invocation.getArgument<Function<File, UUID>>(2)
            postFile.apply(mockFile)
            postFile.apply(mockFile)
            instanceObject
        }

        val incoming = Any()
        val exception = assertThrows<ResponseStatusException> { instanceProcessor.processInstance(authentication, incoming) }

        assertThat(exception).hasMessageContaining("File upload failed")
    }

}