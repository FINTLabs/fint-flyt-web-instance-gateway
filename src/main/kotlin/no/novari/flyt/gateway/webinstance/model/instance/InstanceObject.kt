package no.novari.flyt.gateway.webinstance.model.instance

data class InstanceObject(
    val valuePerKey: Map<String, String> = HashMap(),
    val objectCollectionPerKey: MutableMap<String, Collection<InstanceObject>> = HashMap(),
)
