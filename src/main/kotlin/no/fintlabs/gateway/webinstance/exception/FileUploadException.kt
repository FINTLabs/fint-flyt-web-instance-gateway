package no.fintlabs.gateway.webinstance.exception

import no.fintlabs.gateway.webinstance.model.File

class FileUploadException(
    private val file: File,
    postResponse: String
) : RuntimeException("Could not post file=$file. POST response='$postResponse'")