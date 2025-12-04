package no.novari.gateway.webinstance.exception

import no.novari.gateway.webinstance.model.File

class FileUploadException(
    val file: File,
    postResponse: String,
    cause: Throwable? = null,
) : RuntimeException("Could not post file=$file. POST response='$postResponse'", cause)
