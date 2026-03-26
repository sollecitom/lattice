package sollecitom.libs.lattice.event_store.test.utils

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.event_store.domain.VersionedEvent

fun <EVENT> VersionedEvent.Companion.create(event: EVENT, version: AggregateVersion = AggregateVersion.initial, aggregateId: AggregateId = AggregateId.testRandom()): VersionedEvent<EVENT> =
    VersionedEvent(event = event, version = version, aggregateId = aggregateId)
