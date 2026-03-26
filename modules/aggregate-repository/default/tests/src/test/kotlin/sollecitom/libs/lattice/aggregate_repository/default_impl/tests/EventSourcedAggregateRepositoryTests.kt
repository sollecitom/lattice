package sollecitom.libs.lattice.aggregate_repository.default_impl.tests

import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.EventSourcedAggregateRepository
import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.SnapshotPolicy
import sollecitom.libs.lattice.aggregate_repository.domain.AggregateRepository
import sollecitom.libs.lattice.aggregate_repository.test.specification.AggregateRepositoryTestSpecification
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventStore
import sollecitom.libs.lattice.recovery_store.in_memory.implementation.InMemoryRecoveryStore

class EventSourcedAggregateRepositoryTests : AggregateRepositoryTestSpecification<CounterCommand, CounterEvent, CounterState> {

    override val repository: AggregateRepository<CounterCommand, CounterEvent, CounterState> = EventSourcedAggregateRepository(
        aggregate = CounterAggregate,
        eventStore = InMemoryEventStore(),
        recoveryStore = InMemoryRecoveryStore(),
        snapshotPolicy = SnapshotPolicy.everyNEvents(5),
    )

    override val expectedInitialState = CounterState(value = 0)

    override fun acceptedCommand(): CounterCommand = CounterCommand.Increment(1)

    override fun rejectedCommand(): CounterCommand = CounterCommand.Decrement(100)
}
