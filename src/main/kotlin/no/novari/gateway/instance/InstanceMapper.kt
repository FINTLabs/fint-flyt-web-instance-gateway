package no.novari.gateway.instance

import no.novari.gateway.instance.model.File
import no.novari.gateway.instance.model.instance.InstanceObject
import java.util.UUID

interface InstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (File) -> UUID,
    ): InstanceObject
}
