package sollecitom.libs.lattice.recovery_store.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId

interface RecoveryStore<STATE> {

    suspend fun get(aggregateId: AggregateId): Snapshot<STATE>?

    suspend fun save(snapshot: Snapshot<STATE>)

    companion object
}
