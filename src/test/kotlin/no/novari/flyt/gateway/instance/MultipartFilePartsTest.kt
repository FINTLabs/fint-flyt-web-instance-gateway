package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.webinstance.MultipartFileParts
import no.novari.flyt.gateway.webinstance.exception.MultipartFileReferenceException
import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

class MultipartFilePartsTest {
    @Test
    fun shouldResolveFilePartByPartName() {
        val multipartFile =
            MockMultipartFile(
                "document",
                "document.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test".toByteArray(),
            )

        val resolved = MultipartFileParts(listOf(multipartFile)).resolve(MultipartFileReference(partName = "document"))

        assertThat(resolved).isSameAs(multipartFile)
    }

    @Test
    fun shouldResolveFilePartByOriginalFilenameWhenPartNameIsShared() {
        val firstFile =
            MockMultipartFile(
                "documents",
                "first.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "first".toByteArray(),
            )
        val secondFile =
            MockMultipartFile(
                "documents",
                "second.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "second".toByteArray(),
            )

        val resolved =
            MultipartFileParts(listOf(firstFile, secondFile))
                .resolve(
                    MultipartFileReference(
                        partName = "documents",
                        originalFilename = "second.pdf",
                    ),
                )

        assertThat(resolved).isSameAs(secondFile)
    }

    @Test
    fun shouldIgnoreFileNameWhenMatchingFilePart() {
        val multipartFile =
            MockMultipartFile(
                "document",
                "client-original.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test".toByteArray(),
            )

        val resolved =
            MultipartFileParts(listOf(multipartFile))
                .resolve(
                    MultipartFileReference(
                        partName = "document",
                        fileName = "renamed-application.pdf",
                    ),
                )

        assertThat(resolved).isSameAs(multipartFile)
    }

    @Test
    fun shouldRejectAmbiguousFilePartReference() {
        val firstFile =
            MockMultipartFile(
                "documents",
                "first.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "first".toByteArray(),
            )
        val secondFile =
            MockMultipartFile(
                "documents",
                "second.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "second".toByteArray(),
            )

        val exception =
            assertThrows<MultipartFileReferenceException> {
                MultipartFileParts(listOf(firstFile, secondFile))
                    .resolve(MultipartFileReference(partName = "documents"))
            }

        assertThat(exception.message).contains("Multiple multipart file parts")
    }
}
