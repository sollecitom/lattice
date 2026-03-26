package sollecitom.libs.lattice.aggregate_repository.default_impl.implementation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome

class SequentialAggregateRepository<COMMAND, EVENT, STATE>(
    private val delegate: AggregateRepository<COMMAND, EVENT, STATE>,
) : AggregateRepository<COMMAND, EVENT, STATE> {

    private val mutexes = mutableMapOf<AggregateId, Mutex>()
    private val globalMutex = Mutex()

    override suspend fun handle(aggregateId: AggregateId, command: COMMAND): CommandOutcome<EVENT> {

        val mutex = globalMutex.withLock { mutexes.getOrPut(aggregateId) { Mutex() } }
        return mutex.withLock { delegate.handle(aggregateId, command) }
    }

    override suspend fun state(aggregateId: AggregateId): STATE {

        val mutex = globalMutex.withLock { mutexes.getOrPut(aggregateId) { Mutex() } }
        return mutex.withLock { delegate.state(aggregateId) }
    }

    companion object
}
