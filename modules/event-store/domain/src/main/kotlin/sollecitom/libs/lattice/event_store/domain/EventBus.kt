package sollecitom.libs.lattice.event_store.domain

import kotlinx.coroutines.flow.Flow
import sollecitom.libs.lattice.aggregate.domain.AggregateType

interface EventBus<EVENT> {

    suspend fun publish(event: VersionedEvent<EVENT>)

    fun subscribe(aggregateType: AggregateType? = null): Flow<VersionedEvent<EVENT>>

    companion object
}
