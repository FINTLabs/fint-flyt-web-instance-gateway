package no.novari.gateway.webinstance.model

data class Integration(
    val id: Long,
    val sourceApplicationId: Long?,
    val sourceApplicationIntegrationId: String?,
    val destination: String?,
    val state: State,
    var activeConfigurationId: Long?,
) {
    enum class State {
        ACTIVE,
        DEACTIVATED,
    }
}
