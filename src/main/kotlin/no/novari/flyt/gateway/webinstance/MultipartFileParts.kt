package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.exception.MultipartFileReferenceException
import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import org.springframework.web.multipart.MultipartFile

class MultipartFileParts(
    multipartFiles: Collection<MultipartFile>,
) {
    private val multipartFiles = multipartFiles.toList()

    fun resolve(reference: MultipartFileReference): MultipartFile {
        val matchingPartName =
            multipartFiles.filter { multipartFile ->
                multipartFile.name == reference.partName
            }

        if (matchingPartName.isEmpty()) {
            throw MultipartFileReferenceException(
                "No multipart file part found for provided file reference",
            )
        }

        val matchingFilename = reference.originalFilename ?: reference.fileName
        val matchingFiles =
            if (matchingFilename.isNullOrBlank()) {
                matchingPartName
            } else {
                matchingPartName.filter { multipartFile ->
                    multipartFile.originalFilename == matchingFilename
                }
            }

        return when (matchingFiles.size) {
            1 -> {
                matchingFiles.single()
            }

            0 -> {
                throw MultipartFileReferenceException(
                    "No multipart file part found for provided file reference",
                )
            }

            else -> {
                throw MultipartFileReferenceException(
                    "Multiple multipart file parts found for provided file reference. " +
                        "Set originalFilename in MultipartFileReference to identify the file part.",
                )
            }
        }
    }
}
