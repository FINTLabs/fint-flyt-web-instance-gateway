package no.novari.gateway.instance.exception

import no.novari.gateway.instance.model.File

class FileUploadException(
    val file: File,
    postResponse: String,
    cause: Throwable? = null,
) : RuntimeException("Could not post file=$file. POST response='$postResponse'", cause)
