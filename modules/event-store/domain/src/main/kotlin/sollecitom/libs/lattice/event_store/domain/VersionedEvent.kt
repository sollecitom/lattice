package sollecitom.libs.lattice.event_store.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

data class VersionedEvent<out EVENT>(val event: EVENT, val version: AggregateVersion, val aggregateId: AggregateId) {

    companion object
}
