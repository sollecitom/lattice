package sollecitom.libs.lattice.aggregate_repository.default_impl.tests

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.AggregateId
import sollecitom.libs.lattice.aggregate.test.utils.testRandom
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.EventSourcedAggregateRepository
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.SnapshotPolicy
import sollecitom.libs.lattice.aggregate_repository.domain.CommandOutcome
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventStore
import sollecitom.libs.lattice.recovery_store.in_memory.implementation.InMemoryRecoveryStore

@Suppress("FunctionName")
class EventSourcedAggregateRepositoryAdditionalTests {

    @Test
    fun `state is recovered from snapshot plus delta events`() = runTest {

        val eventStore = InMemoryEventStore<CounterEvent>()
        val recoveryStore = InMemoryRecoveryStore<CounterState>()
        val repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = eventStore,
            recoveryStore = recoveryStore,
            snapshotPolicy = SnapshotPolicy.everyNEvents(3),
        )
        val aggregateId = AggregateId.testRandom()

        repeat(5) { repository.handle(aggregateId, CounterCommand.Increment(1)) }

        val state = repository.state(aggregateId)
        assertThat(state).isEqualTo(CounterState(value = 5))
    }

    @Test
    fun `repository works without a recovery store`() = runTest {

        val repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = InMemoryEventStore(),
            recoveryStore = null,
        )
        val aggregateId = AggregateId.testRandom()

        repository.handle(aggregateId, CounterCommand.Increment(3))
        repository.handle(aggregateId, CounterCommand.Increment(2))
        val state = repository.state(aggregateId)

        assertThat(state).isEqualTo(CounterState(value = 5))
    }

    @Test
    fun `rejected command does not change state`() = runTest {

        val repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = InMemoryEventStore(),
        )
        val aggregateId = AggregateId.testRandom()

        repository.handle(aggregateId, CounterCommand.Increment(2))
        val outcome = repository.handle(aggregateId, CounterCommand.Decrement(5))

        assertThat(outcome).isInstanceOf<CommandOutcome.Rejected>()
        val state = repository.state(aggregateId)
        assertThat(state).isEqualTo(CounterState(value = 2))
    }

    @Test
    fun `snapshot policy never does not save snapshots`() = runTest {

        val recoveryStore = InMemoryRecoveryStore<CounterState>()
        val repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = InMemoryEventStore(),
            recoveryStore = recoveryStore,
            snapshotPolicy = SnapshotPolicy.never,
        )
        val aggregateId = AggregateId.testRandom()

        repeat(10) { repository.handle(aggregateId, CounterCommand.Increment(1)) }

        val snapshot = recoveryStore.get(aggregateId)
        assertThat(snapshot).isNull()
    }

    @Test
    fun `snapshot policy always saves after every command`() = runTest {

        val recoveryStore = InMemoryRecoveryStore<CounterState>()
        val repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = InMemoryEventStore(),
            recoveryStore = recoveryStore,
            snapshotPolicy = SnapshotPolicy.always,
        )
        val aggregateId = AggregateId.testRandom()

        repository.handle(aggregateId, CounterCommand.Increment(7))

        val snapshot = recoveryStore.get(aggregateId)
        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.state).isEqualTo(CounterState(value = 7))
    }
}
