package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.model.MultipartFileReference
import no.novari.flyt.gateway.webinstance.model.instance.InstanceObject
import java.util.UUID

interface MultipartInstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (MultipartFileReference) -> UUID,
    ): InstanceObject
}
