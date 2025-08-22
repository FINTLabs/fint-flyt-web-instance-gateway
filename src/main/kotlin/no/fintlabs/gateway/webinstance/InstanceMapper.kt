package no.fintlabs.gateway.webinstance

import no.fintlabs.gateway.webinstance.model.File
import no.fintlabs.gateway.webinstance.model.instance.InstanceObject
import java.util.UUID
import java.util.function.Function

interface InstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: Function<File, UUID>,
    ): InstanceObject
}
