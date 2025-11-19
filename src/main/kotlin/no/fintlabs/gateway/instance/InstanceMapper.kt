package no.fintlabs.gateway.instance

import no.fintlabs.gateway.instance.model.File
import no.fintlabs.gateway.instance.model.instance.InstanceObject
import java.util.UUID

interface InstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (File) -> UUID,
    ): InstanceObject
}
