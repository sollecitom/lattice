package sollecitom.libs.lattice.recovery_store.test.specification

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.recovery_store.domain.RecoveryStore
import sollecitom.libs.lattice.recovery_store.domain.Snapshot

@Suppress("FunctionName")
interface RecoveryStoreTestSpecification {

    val recoveryStore: RecoveryStore<String>

    @Test
    fun `getting a snapshot for an unknown aggregate returns null`() = runTest {

        val aggregateId = AggregateId.testRandom()

        val snapshot = recoveryStore.get(aggregateId)

        assertThat(snapshot).isNull()
    }

    @Test
    fun `a saved snapshot can be retrieved`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val snapshot = Snapshot(state = "some-state", version = AggregateVersion(5), aggregateId = aggregateId)
        recoveryStore.save(snapshot)

        val retrieved = recoveryStore.get(aggregateId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.state).isEqualTo("some-state")
        assertThat(retrieved.version).isEqualTo(AggregateVersion(5))
        assertThat(retrieved.aggregateId).isEqualTo(aggregateId)
    }

    @Test
    fun `saving a newer snapshot replaces the previous one`() = runTest {

        val aggregateId = AggregateId.testRandom()
        recoveryStore.save(Snapshot(state = "old-state", version = AggregateVersion(3), aggregateId = aggregateId))
        recoveryStore.save(Snapshot(state = "new-state", version = AggregateVersion(7), aggregateId = aggregateId))

        val retrieved = recoveryStore.get(aggregateId)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.state).isEqualTo("new-state")
        assertThat(retrieved.version).isEqualTo(AggregateVersion(7))
    }

    @Test
    fun `snapshots for different aggregates are isolated`() = runTest {

        val aggregateId1 = AggregateId.testRandom()
        val aggregateId2 = AggregateId.testRandom()
        recoveryStore.save(Snapshot(state = "state-1", version = AggregateVersion(1), aggregateId = aggregateId1))
        recoveryStore.save(Snapshot(state = "state-2", version = AggregateVersion(2), aggregateId = aggregateId2))

        val snapshot1 = recoveryStore.get(aggregateId1)
        val snapshot2 = recoveryStore.get(aggregateId2)

        assertThat(snapshot1).isNotNull()
        assertThat(snapshot1!!.state).isEqualTo("state-1")
        assertThat(snapshot2).isNotNull()
        assertThat(snapshot2!!.state).isEqualTo("state-2")
    }
}
