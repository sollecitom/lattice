package sollecitom.libs.lattice.event_store.test.specification

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.domain.AggregateVersion
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.event_store.domain.EventStore
import sollecitom.libs.lattice.event_store.domain.OptimisticConcurrencyException

@Suppress("FunctionName")
interface EventStoreTestSpecification {

    val eventStore: EventStore<String>

    @Test
    fun `appending events to a new aggregate returns the new version`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val events = listOf("event-1", "event-2")

        val newVersion = eventStore.append(aggregateId, events, AggregateVersion.initial)

        assertThat(newVersion).isEqualTo(AggregateVersion(2))
    }

    @Test
    fun `reading events from an empty aggregate returns no events`() = runTest {

        val aggregateId = AggregateId.testRandom()

        val events = eventStore.read(aggregateId).toList()

        assertThat(events).isEmpty()
    }

    @Test
    fun `events can be read back after appending`() = runTest {

        val aggregateId = AggregateId.testRandom()
        val events = listOf("event-1", "event-2")
        eventStore.append(aggregateId, events, AggregateVersion.initial)

        val readEvents = eventStore.read(aggregateId).toList()

        assertThat(readEvents).hasSize(2)
        assertThat(readEvents.map { it.event }).isEqualTo(events)
        assertThat(readEvents[0].version).isEqualTo(AggregateVersion(1))
        assertThat(readEvents[1].version).isEqualTo(AggregateVersion(2))
        assertThat(readEvents.all { it.aggregateId == aggregateId }).isTrue()
    }

    @Test
    fun `events can be appended in multiple batches`() = runTest {

        val aggregateId = AggregateId.testRandom()
        eventStore.append(aggregateId, listOf("event-1"), AggregateVersion.initial)
        eventStore.append(aggregateId, listOf("event-2", "event-3"), AggregateVersion(1))

        val readEvents = eventStore.read(aggregateId).toList()

        assertThat(readEvents).hasSize(3)
        assertThat(readEvents.map { it.event }).isEqualTo(listOf("event-1", "event-2", "event-3"))
        assertThat(readEvents[0].version).isEqualTo(AggregateVersion(1))
        assertThat(readEvents[1].version).isEqualTo(AggregateVersion(2))
        assertThat(readEvents[2].version).isEqualTo(AggregateVersion(3))
    }

    @Test
    fun `reading from a specific version returns only newer events`() = runTest {

        val aggregateId = AggregateId.testRandom()
        eventStore.append(aggregateId, listOf("event-1", "event-2", "event-3"), AggregateVersion.initial)

        val readEvents = eventStore.read(aggregateId, fromVersion = AggregateVersion(2)).toList()

        assertThat(readEvents).hasSize(1)
        assertThat(readEvents[0].event).isEqualTo("event-3")
        assertThat(readEvents[0].version).isEqualTo(AggregateVersion(3))
    }

    @Test
    fun `optimistic concurrency conflict is detected`() = runTest {

        val aggregateId = AggregateId.testRandom()
        eventStore.append(aggregateId, listOf("event-1"), AggregateVersion.initial)

        val exception = runCatching {
            eventStore.append(aggregateId, listOf("event-conflict"), AggregateVersion.initial)
        }.exceptionOrNull()

        assertThat(exception).isNotNull().isInstanceOf<OptimisticConcurrencyException>()
    }

    @Test
    fun `events for different aggregates are isolated`() = runTest {

        val aggregateId1 = AggregateId.testRandom()
        val aggregateId2 = AggregateId.testRandom()
        eventStore.append(aggregateId1, listOf("agg1-event-1"), AggregateVersion.initial)
        eventStore.append(aggregateId2, listOf("agg2-event-1", "agg2-event-2"), AggregateVersion.initial)

        val events1 = eventStore.read(aggregateId1).toList()
        val events2 = eventStore.read(aggregateId2).toList()

        assertThat(events1).hasSize(1)
        assertThat(events1[0].event).isEqualTo("agg1-event-1")
        assertThat(events2).hasSize(2)
        assertThat(events2.map { it.event }).isEqualTo(listOf("agg2-event-1", "agg2-event-2"))
    }

    @Test
    fun `appending an empty list of events returns the same version`() = runTest {

        val aggregateId = AggregateId.testRandom()

        val newVersion = eventStore.append(aggregateId, emptyList(), AggregateVersion.initial)

        assertThat(newVersion).isEqualTo(AggregateVersion.initial)
    }
}
