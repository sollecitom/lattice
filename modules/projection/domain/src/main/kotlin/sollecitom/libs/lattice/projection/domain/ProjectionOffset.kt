package sollecitom.libs.lattice.projection.domain

import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

data class ProjectionOffset(val offsets: Map<AggregateId, AggregateVersion>) {

    fun versionFor(aggregateId: AggregateId): AggregateVersion = offsets[aggregateId] ?: AggregateVersion.initial

    companion object {

        val empty = ProjectionOffset(emptyMap())
    }
}
