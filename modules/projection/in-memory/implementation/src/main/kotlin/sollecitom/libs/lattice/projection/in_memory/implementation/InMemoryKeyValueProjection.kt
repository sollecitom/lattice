package sollecitom.libs.lattice.projection.in_memory.implementation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.event_store.domain.VersionedEvent
import sollecitom.libs.lattice.projection.domain.EventProjection
import sollecitom.libs.lattice.projection.domain.ProjectionOffset

class InMemoryKeyValueProjection<EVENT, VIEW>(
    private val project: (VIEW?, VersionedEvent<EVENT>) -> VIEW,
) : EventProjection<EVENT> {

    private val views = mutableMapOf<AggregateId, VIEW>()
    private val offsets = mutableMapOf<AggregateId, AggregateVersion>()
    private val mutex = Mutex()

    override suspend fun process(event: VersionedEvent<EVENT>): Unit = mutex.withLock {

        val currentView = views[event.aggregateId]
        views[event.aggregateId] = project(currentView, event)
        offsets[event.aggregateId] = event.version
    }

    suspend fun get(aggregateId: AggregateId): VIEW? = mutex.withLock {
        views[aggregateId]
    }

    suspend fun offset(): ProjectionOffset = mutex.withLock {
        ProjectionOffset(offsets.toMap())
    }

    companion object
}
