package no.fintlabs.gateway.instance.model.instance

data class InstanceObject(
    val valuePerKey: Map<String, String> = HashMap(),
    val objectCollectionPerKey: MutableMap<String, Collection<InstanceObject>> = HashMap(),
)
