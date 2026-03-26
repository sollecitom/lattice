package sollecitom.libs.lattice.recovery_store.in_memory.implementation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.recovery_store.domain.RecoveryStore
import sollecitom.libs.lattice.recovery_store.domain.Snapshot

class InMemoryRecoveryStore<STATE> : RecoveryStore<STATE> {

    private val snapshots = mutableMapOf<AggregateId, Snapshot<STATE>>()
    private val mutex = Mutex()

    override suspend fun get(aggregateId: AggregateId): Snapshot<STATE>? = mutex.withLock {
        snapshots[aggregateId]
    }

    override suspend fun save(snapshot: Snapshot<STATE>): Unit = mutex.withLock {
        snapshots[snapshot.aggregateId] = snapshot
    }

    companion object
}
