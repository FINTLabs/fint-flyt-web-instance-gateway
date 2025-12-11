package no.novari.flyt.gateway.webinstance

enum class ErrorCode {
    GENERAL_SYSTEM_ERROR,
    INSTANCE_VALIDATION_ERROR,
    INSTANCE_REJECTED_ERROR,
    FILE_UPLOAD_ERROR,
    NO_INTEGRATION_FOUND_ERROR,
    INTEGRATION_DEACTIVATED_ERROR,
    ;

    companion object {
        private const val ERROR_PREFIX = "FINT_FLYT_INSTANCE_GATEWAY_"
    }

    fun getCode(): String {
        return ERROR_PREFIX + this.name
    }
}
