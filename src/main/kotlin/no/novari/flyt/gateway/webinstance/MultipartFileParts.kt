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

        if (matchingPartName.size == 1) {
            return matchingPartName.single()
        }

        val matchingOriginalFilename = reference.originalFilename
        if (matchingOriginalFilename.isNullOrBlank()) {
            throw MultipartFileReferenceException(
                "Multiple multipart file parts found for provided file reference. " +
                    "Set originalFilename in MultipartFileReference to identify the file part.",
            )
        }

        val matchingFiles =
            matchingPartName.filter { multipartFile ->
                multipartFile.originalFilename == matchingOriginalFilename
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
