package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.webinstance.FileClient
import no.novari.flyt.gateway.webinstance.MultipartFileParts
import no.novari.flyt.gateway.webinstance.exception.FileUploadException
import no.novari.flyt.gateway.webinstance.exception.MultipartFileReferenceException
import no.novari.flyt.gateway.webinstance.exception.MultipartFileUploadException
import no.novari.flyt.gateway.webinstance.model.File
import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import no.novari.flyt.gateway.webinstance.model.MultipartFileUpload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets

class FileLoggingSafetyTest {
    @Test
    fun shouldNotExposeJsonFileNameInLoggableStrings() {
        val file =
            File(
                name = SENSITIVE_FILE_NAME,
                sourceApplicationId = 123L,
                sourceApplicationInstanceId = "instance-id",
                type = MediaType.APPLICATION_PDF,
                encoding = "base64",
                base64Contents = "dGVzdA==",
            )

        assertThat(file.toString()).doesNotContain(SENSITIVE_FILE_NAME)
        assertThat(FileUploadException(file, "failed").message).doesNotContain(SENSITIVE_FILE_NAME)
    }

    @Test
    fun shouldNotExposeMultipartFileNameInLoggableStrings() {
        val multipartFile =
            MockMultipartFile(
                "document",
                SENSITIVE_FILE_NAME,
                MediaType.APPLICATION_PDF_VALUE,
                "test".toByteArray(),
            )
        val file =
            MultipartFileUpload(
                name = SENSITIVE_FILE_NAME,
                sourceApplicationId = 123L,
                sourceApplicationInstanceId = "instance-id",
                type = MediaType.APPLICATION_PDF,
                encoding = "binary",
                multipartFile = multipartFile,
            )

        assertThat(file.toString()).doesNotContain(SENSITIVE_FILE_NAME)
        assertThat(MultipartFileUploadException(file, "failed").message).doesNotContain(SENSITIVE_FILE_NAME)
    }

    @Test
    fun shouldNotExposeMultipartOriginalFilenameInReferenceErrors() {
        val exception =
            assertThrows<MultipartFileReferenceException> {
                MultipartFileParts(emptyList())
                    .resolve(
                        MultipartFileReference(
                            partName = SENSITIVE_FILE_NAME,
                            originalFilename = SENSITIVE_FILE_NAME,
                        ),
                    )
            }

        assertThat(exception.message).doesNotContain(SENSITIVE_FILE_NAME)
        assertThat(exception.cause).isNull()
    }

    @Test
    fun shouldNotExposeFileNameFromFileServiceResponseBodyInRecoverException() {
        val file =
            File(
                name = SENSITIVE_FILE_NAME,
                sourceApplicationId = 123L,
                sourceApplicationInstanceId = "instance-id",
                type = MediaType.APPLICATION_PDF,
                encoding = "base64",
                base64Contents = "dGVzdA==",
            )
        val fileClient = FileClient(mock<RestClient>(), "http://file-service")
        val responseException = fileServiceExceptionWithSensitiveBody()

        val exception =
            assertThrows<FileUploadException> {
                fileClient.recover(responseException, file)
            }

        assertThat(exception.message).doesNotContain(SENSITIVE_FILE_NAME)
        assertThat(exception.cause).isNull()
    }

    @Test
    fun shouldNotExposeMultipartFileNameFromFileServiceResponseBodyInRecoverException() {
        val multipartFile =
            MockMultipartFile(
                "document",
                SENSITIVE_FILE_NAME,
                MediaType.APPLICATION_PDF_VALUE,
                "test".toByteArray(),
            )
        val file =
            MultipartFileUpload(
                name = SENSITIVE_FILE_NAME,
                sourceApplicationId = 123L,
                sourceApplicationInstanceId = "instance-id",
                type = MediaType.APPLICATION_PDF,
                encoding = "binary",
                multipartFile = multipartFile,
            )
        val fileClient = FileClient(mock<RestClient>(), "http://file-service")
        val responseException = fileServiceExceptionWithSensitiveBody()

        val exception =
            assertThrows<MultipartFileUploadException> {
                fileClient.recover(responseException, file)
            }

        assertThat(exception.message).doesNotContain(SENSITIVE_FILE_NAME)
    }

    private fun fileServiceExceptionWithSensitiveBody(): HttpClientErrorException =
        HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            "Could not upload $SENSITIVE_FILE_NAME".toByteArray(),
            StandardCharsets.UTF_8,
        )

    private companion object {
        private const val SENSITIVE_FILE_NAME = "diagnose-ola-nordmann.pdf"
    }
}
