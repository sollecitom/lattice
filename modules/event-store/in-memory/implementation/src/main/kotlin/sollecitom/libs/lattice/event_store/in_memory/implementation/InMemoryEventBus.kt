package sollecitom.libs.lattice.event_store.in_memory.implementation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import sollecitom.libs.lattice.aggregate.domain.AggregateType
import sollecitom.libs.lattice.event_store.domain.EventBus
import sollecitom.libs.lattice.event_store.domain.VersionedEvent

class InMemoryEventBus<EVENT>(
    private val aggregateTypeResolver: (EVENT) -> AggregateType,
) : EventBus<EVENT> {

    private val flow = MutableSharedFlow<VersionedEvent<EVENT>>(extraBufferCapacity = 1024)

    override suspend fun publish(event: VersionedEvent<EVENT>) {
        flow.emit(event)
    }

    override fun subscribe(aggregateType: AggregateType?): Flow<VersionedEvent<EVENT>> {
        val sharedFlow = flow.asSharedFlow()
        return if (aggregateType == null) sharedFlow
        else sharedFlow.filter { aggregateTypeResolver(it.event) == aggregateType }
    }

    companion object
}
