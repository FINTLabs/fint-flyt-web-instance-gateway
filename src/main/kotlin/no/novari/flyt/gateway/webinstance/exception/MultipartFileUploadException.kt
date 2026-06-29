package no.novari.flyt.gateway.webinstance.exception

import no.novari.flyt.gateway.webinstance.model.MultipartFileUpload

class MultipartFileUploadException(
    val file: MultipartFileUpload,
    postResponse: String,
    cause: Throwable? = null,
) : RuntimeException("Could not post file=$file. POST response='$postResponse'", cause)
