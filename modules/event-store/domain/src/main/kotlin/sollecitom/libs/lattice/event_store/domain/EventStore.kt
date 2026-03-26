package sollecitom.libs.lattice.event_store.domain

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

interface EventStore<EVENT> {

    suspend fun append(aggregateId: AggregateId, events: List<EVENT>, expectedVersion: AggregateVersion): AggregateVersion

    fun read(aggregateId: AggregateId, fromVersion: AggregateVersion = AggregateVersion.initial): Flow<VersionedEvent<EVENT>>

    companion object
}
