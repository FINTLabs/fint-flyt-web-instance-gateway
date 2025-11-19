package no.fintlabs.gateway.instance

import no.fintlabs.gateway.instance.exception.FileUploadException
import no.fintlabs.gateway.instance.model.File
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.util.UUID

@Service
class FileClient(
    @Qualifier("fileRestTemplate") private val fileRestTemplate: RestTemplate,
) {
    companion object {
        private const val MAX_RETRIES: Int = 5
        private const val RETRY_DELAY = 1_000L
    }

    fun postFile(file: File): UUID {
        var lastException: HttpStatusCodeException? = null

        repeat(MAX_RETRIES) { attemptIndex ->
            try {
                val response: ResponseEntity<UUID> =
                    fileRestTemplate.postForEntity(
                        "/upload",
                        file,
                        UUID::class.java,
                    )

                if (response.statusCode.is2xxSuccessful) {
                    return response.body ?: throw FileUploadException(file, "Empty response body", lastException)
                }

                val errorBody = response.body?.toString() ?: "Unknown error"
                throw FileUploadException(file, errorBody, lastException)
            } catch (ex: HttpStatusCodeException) {
                lastException = ex
                if (attemptIndex < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_DELAY)
                }
            }
        }

        throw FileUploadException(
            file,
            lastException?.responseBodyAsString ?: "Failed after $MAX_RETRIES attempts",
            lastException,
        )
    }
}
