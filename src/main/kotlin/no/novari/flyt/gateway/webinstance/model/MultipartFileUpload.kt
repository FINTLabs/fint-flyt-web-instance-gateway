package no.novari.flyt.gateway.webinstance.model

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

data class MultipartFileUpload(
    val name: String,
    val sourceApplicationId: Long,
    val sourceApplicationInstanceId: String,
    @field:JsonSerialize(using = ToStringSerializer::class)
    val type: MediaType,
    val encoding: String,
    val multipartFile: MultipartFile,
) {
    override fun toString(): String {
        return buildString {
            append("${MultipartFileUpload::class.java.simpleName}[")
            append("sourceApplicationId=").append(sourceApplicationId).append(", ")
            append("sourceApplicationInstanceId='").append(sourceApplicationInstanceId).append("', ")
            append("type=").append(type).append(", ")
            append("encoding='").append(encoding).append("', ")
            append("contentLength=").append(multipartFile.size)
            append("]")
        }
    }
}
