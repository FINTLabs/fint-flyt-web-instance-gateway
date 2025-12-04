package no.novari.gateway.webinstance

import no.novari.gateway.webinstance.model.File
import no.novari.gateway.webinstance.model.instance.InstanceObject
import java.util.UUID

interface InstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (File) -> UUID,
    ): InstanceObject
}
