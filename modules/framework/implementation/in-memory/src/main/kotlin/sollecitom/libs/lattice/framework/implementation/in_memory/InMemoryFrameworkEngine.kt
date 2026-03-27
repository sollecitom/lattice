package sollecitom.libs.lattice.framework.implementation.in_memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sollecitom.libs.lattice.framework.api.FrameworkEngine
import sollecitom.libs.lattice.sdk.kotlin.api.*
import sollecitom.libs.swissknife.core.domain.identity.Id

class InMemoryFrameworkEngine : FrameworkEngine {

    private val registeredAggregates = mutableListOf<RegisteredAggregate>()
    private val mutex = Mutex()

    override fun <COMMAND : Command, EVENT, STATE> registerAggregate(
        type: String,
        aggregate: Aggregate<COMMAND, EVENT, STATE>,
        bindings: Bindings,
    ) {
        registeredAggregates.add(RegisteredAggregate(type, aggregate, bindings))
    }

    override fun canHandleCommand(command: Command): Boolean = aggregateFor(command) != null

    @Suppress("UNCHECKED_CAST")
    override suspend fun processCommand(command: Command): Decision<*> = mutex.withLock {

        val registered = aggregateFor(command)
            ?: return@withLock Decision.Reject("No aggregate registered for command type ${command.javaClass.name}")

        val routingKey = registered.bindings.extractKeyForCommand(command)
            ?: return@withLock Decision.Reject("No routing key binding for command type ${command.javaClass.name}")

        val aggregate = registered.aggregate as Aggregate<Command, Any, Any>
        val instance = registered.instances.getOrPut(routingKey) { AggregateInstance(state = aggregate.initialState) }

        // Store command-as-event in the unified topic (audit trail)
        instance.events.add(CommandReceived(command))

        val decision = aggregate.handle(instance.state, command)

        // TODO on replay: skip CommandReceived markers — their effects are captured by the result events
        when (decision) {
            is Decision.Accept -> {
                val event = decision.event
                if (event is Event) instance.events.add(event)
                instance.state = aggregate.apply(instance.state, event!!)
                decision
            }

            is Decision.AcceptWithResponse<*, *> -> {
                val event = decision.event
                if (event is Event) instance.events.add(event)
                instance.state = aggregate.apply(instance.state, event!!)
                decision
            }

            is Decision.Reject -> decision
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun processEvent(event: Event): Unit = mutex.withLock {

        for (registered in registeredAggregates) {
            if (!registered.bindings.handlesEvent(event)) continue

            val routingKey = registered.bindings.extractKeyForEvent(event) ?: continue
            val aggregate = registered.aggregate as Aggregate<Command, Any, Any>
            val instance = registered.instances.getOrPut(routingKey) { AggregateInstance(state = aggregate.initialState) }
            instance.state = aggregate.apply(instance.state, event)
            instance.events.add(event)
        }
    }

    override fun eventHistory(targetId: Id): Flow<Event> {

        val events = registeredAggregates.flatMap { registered ->
            registered.instances[targetId]?.events ?: emptyList()
        }
        return events.asFlow()
    }

    override suspend fun stop() {}

    private fun aggregateFor(command: Command): RegisteredAggregate? =
        registeredAggregates.find { it.bindings.handlesCommand(command) }

    private class AggregateInstance(var state: Any, val events: MutableList<Event> = mutableListOf())

    private class RegisteredAggregate(
        val type: String,
        val aggregate: Aggregate<*, *, *>,
        val bindings: Bindings,
        val instances: MutableMap<Id, AggregateInstance> = mutableMapOf(),
    )

    companion object
}
