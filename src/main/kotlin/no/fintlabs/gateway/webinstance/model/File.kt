package no.fintlabs.gateway.webinstance.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import org.springframework.http.MediaType

data class File(
    val name: String,
    val sourceApplicationId: Long,
    val sourceApplicationInstanceId: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val type: MediaType,
    val encoding: String,
    @JsonProperty("contents")
    val base64Contents: String
) {
    override fun toString(): String {
        return buildString {
            append("${File::class.java.simpleName}[")
            append("name='").append(name).append("', ")
            append("sourceApplicationId=").append(sourceApplicationId).append(", ")
            append("sourceApplicationInstanceId='").append(sourceApplicationInstanceId).append("', ")
            append("type=").append(type).append(", ")
            append("encoding='").append(encoding).append("'")
            append("]")
        }
    }
}