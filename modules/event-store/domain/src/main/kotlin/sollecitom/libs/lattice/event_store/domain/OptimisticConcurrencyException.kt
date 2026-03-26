package sollecitom.libs.lattice.event_store.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

class OptimisticConcurrencyException(
    val aggregateId: AggregateId,
    val expectedVersion: AggregateVersion,
    val actualVersion: AggregateVersion
) : RuntimeException("Optimistic concurrency conflict for aggregate ${aggregateId.stringValue}: expected version ${expectedVersion.value}, but actual version is ${actualVersion.value}")
