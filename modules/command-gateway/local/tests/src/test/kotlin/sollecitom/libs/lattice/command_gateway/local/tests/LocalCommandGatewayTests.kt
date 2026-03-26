package sollecitom.libs.lattice.command_gateway.local.tests

import sollecitom.libs.lattice.aggregate_repository.default_impl.implementation.EventSourcedAggregateRepository
import sollecitom.libs.lattice.command_gateway.domain.CommandGateway
import sollecitom.libs.lattice.command_gateway.local.implementation.LocalCommandGateway
import sollecitom.libs.lattice.command_gateway.test.specification.CommandGatewayTestSpecification
import sollecitom.libs.lattice.event_store.in_memory.implementation.InMemoryEventStore

class LocalCommandGatewayTests : CommandGatewayTestSpecification<CounterCommand, CounterEvent> {

    override val gateway: CommandGateway<CounterCommand, CounterEvent> = LocalCommandGateway(
        repository = EventSourcedAggregateRepository(
            aggregate = CounterAggregate,
            eventStore = InMemoryEventStore(),
        )
    )

    override fun acceptedCommand(): CounterCommand = CounterCommand.Increment(1)

    override fun rejectedCommand(): CounterCommand = CounterCommand.Decrement(100)
}
