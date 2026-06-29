package no.novari.flyt.gateway.webinstance.model

import org.springframework.http.MediaType

data class MultipartFileReference(
    val partName: String,
    val fileName: String? = null,
    val originalFilename: String? = null,
    val type: MediaType? = null,
    val encoding: String = "binary",
) {
    init {
        require(partName.isNotBlank()) { "partName must not be blank" }
        require(fileName == null || fileName.isNotBlank()) { "fileName must not be blank" }
        require(originalFilename == null || originalFilename.isNotBlank()) { "originalFilename must not be blank" }
        require(encoding.isNotBlank()) { "encoding must not be blank" }
    }
}
