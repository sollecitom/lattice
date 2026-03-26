package sollecitom.libs.lattice.projection.domain

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.lattice.event_store.domain.VersionedEvent

interface EventProjection<EVENT> {

    suspend fun process(event: VersionedEvent<EVENT>)

    companion object
}

suspend fun <EVENT> EventProjection<EVENT>.processAll(events: Flow<VersionedEvent<EVENT>>) {
    events.collect { process(it) }
}
