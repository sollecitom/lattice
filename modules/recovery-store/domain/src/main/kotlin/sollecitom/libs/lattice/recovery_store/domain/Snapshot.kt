package sollecitom.libs.lattice.recovery_store.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

data class Snapshot<out STATE>(val state: STATE, val version: AggregateVersion, val aggregateId: AggregateId) {

    companion object
}
