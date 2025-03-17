package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.model.File
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.client.RestTemplate
import java.util.*

class FileClient(
    @Qualifier("fileRestTemplate") private val fileRestTemplate: RestTemplate
) {

//    fun postFile(file: File): UUID {
//        val maxRetries = 5
//        var attempts = 0
//        while ()
//    }

}