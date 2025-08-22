package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.exception.FileUploadException
import no.fintlabs.gateway.webinstance.model.File
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.UUID

@Service
class FileClient(
    @Qualifier("fileRestTemplate") private val fileRestTemplate: RestTemplate,
) {
    fun postFile(file: File): UUID {
        val maxRetries = 5
        var lastException: HttpStatusCodeException? = null

        for (attempt in 1..maxRetries) {
            try {
                val response: ResponseEntity<UUID> =
                    fileRestTemplate.postForEntity(
                        "/upload",
                        file,
                        UUID::class.java,
                    )

                if (response.statusCode.isError) {
                    val errorBody = response.body?.toString() ?: "Unknown error"
                    throw FileUploadException(file, errorBody, lastException)
                }

                return response.body ?: throw FileUploadException(file, "Empty response", lastException)
            } catch (ex: HttpStatusCodeException) {
                lastException = ex
                if (attempt < maxRetries) {
                    Thread.sleep(Duration.ofSeconds(1).toMillis())
                }
            }
        }

        throw FileUploadException(
            file,
            lastException?.responseBodyAsString ?: "Failed after $maxRetries attempts",
            lastException,
        )
    }
}
