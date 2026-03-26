package sollecitom.libs.lattice.aggregate_repository.default_impl.implementation

import sollecitom.libs.lattice.aggregate.domain.AggregateVersion

fun interface SnapshotPolicy {

    fun shouldSnapshot(version: AggregateVersion): Boolean

    companion object {

        val never: SnapshotPolicy = SnapshotPolicy { false }

        val always: SnapshotPolicy = SnapshotPolicy { true }

        fun everyNEvents(n: Int): SnapshotPolicy {
            require(n > 0) { "Snapshot interval must be positive, but was $n" }
            return SnapshotPolicy { version -> version.value > 0 && version.value % n == 0L }
        }
    }
}
