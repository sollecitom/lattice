package sollecitom.libs.lattice.projection.in_memory.tests

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.event_store.domain.VersionedEvent
import sollecitom.libs.lattice.projection.in_memory.implementation.InMemoryKeyValueProjection

@Suppress("FunctionName")
class InMemoryKeyValueProjectionTests {

    @Test
    fun `processing events builds a view`() = runTest {

        val projection = InMemoryKeyValueProjection<String, Int> { current, event ->
            (current ?: 0) + event.event.length
        }
        val aggregateId = AggregateId.testRandom()

        projection.process(VersionedEvent("hello", AggregateVersion(1), aggregateId))
        projection.process(VersionedEvent("world", AggregateVersion(2), aggregateId))

        val view = projection.get(aggregateId)
        assertThat(view).isNotNull().isEqualTo(10)
    }

    @Test
    fun `views for different aggregates are independent`() = runTest {

        val projection = InMemoryKeyValueProjection<String, Int> { current, event ->
            (current ?: 0) + 1
        }
        val aggregateId1 = AggregateId.testRandom()
        val aggregateId2 = AggregateId.testRandom()

        projection.process(VersionedEvent("a", AggregateVersion(1), aggregateId1))
        projection.process(VersionedEvent("b", AggregateVersion(1), aggregateId2))
        projection.process(VersionedEvent("c", AggregateVersion(2), aggregateId1))

        assertThat(projection.get(aggregateId1)).isEqualTo(2)
        assertThat(projection.get(aggregateId2)).isEqualTo(1)
    }

    @Test
    fun `get returns null for unknown aggregate`() = runTest {

        val projection = InMemoryKeyValueProjection<String, String> { _, event -> event.event }

        val view = projection.get(AggregateId.testRandom())

        assertThat(view).isNull()
    }

    @Test
    fun `offset tracks the latest version per aggregate`() = runTest {

        val projection = InMemoryKeyValueProjection<String, String> { _, event -> event.event }
        val aggregateId = AggregateId.testRandom()

        projection.process(VersionedEvent("a", AggregateVersion(1), aggregateId))
        projection.process(VersionedEvent("b", AggregateVersion(2), aggregateId))

        val offset = projection.offset()
        assertThat(offset.versionFor(aggregateId)).isEqualTo(AggregateVersion(2))
    }
}
