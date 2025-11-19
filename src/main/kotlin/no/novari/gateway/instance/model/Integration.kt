package no.novari.gateway.instance.model

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
