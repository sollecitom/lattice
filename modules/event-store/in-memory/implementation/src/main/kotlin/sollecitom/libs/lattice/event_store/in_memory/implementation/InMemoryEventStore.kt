package sollecitom.libs.lattice.event_store.in_memory.implementation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.event_store.domain.EventStore
import sollecitom.libs.lattice.event_store.domain.OptimisticConcurrencyException
import sollecitom.libs.lattice.event_store.domain.VersionedEvent

class InMemoryEventStore<EVENT> : EventStore<EVENT> {

    private val streams = mutableMapOf<AggregateId, MutableList<VersionedEvent<EVENT>>>()
    private val mutex = Mutex()

    override suspend fun append(aggregateId: AggregateId, events: List<EVENT>, expectedVersion: AggregateVersion): AggregateVersion = mutex.withLock {

        if (events.isEmpty()) return@withLock expectedVersion

        val stream = streams.getOrPut(aggregateId) { mutableListOf() }
        val currentVersion = AggregateVersion(stream.size.toLong())

        if (currentVersion != expectedVersion) {
            throw OptimisticConcurrencyException(aggregateId, expectedVersion, currentVersion)
        }

        events.forEachIndexed { index, event ->
            val version = AggregateVersion(currentVersion.value + index + 1)
            stream.add(VersionedEvent(event = event, version = version, aggregateId = aggregateId))
        }

        AggregateVersion(currentVersion.value + events.size)
    }

    override fun read(aggregateId: AggregateId, fromVersion: AggregateVersion): Flow<VersionedEvent<EVENT>> {

        val stream = streams[aggregateId] ?: return emptyList<VersionedEvent<EVENT>>().asFlow()
        return stream.filter { it.version > fromVersion }.asFlow()
    }

    companion object
}
