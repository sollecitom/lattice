package sollecitom.libs.lattice.recovery_store.test.utils

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.recovery_store.domain.Snapshot

fun <STATE> Snapshot.Companion.create(state: STATE, version: AggregateVersion = AggregateVersion.initial, aggregateId: AggregateId = AggregateId.testRandom()): Snapshot<STATE> =
    Snapshot(state = state, version = version, aggregateId = aggregateId)
