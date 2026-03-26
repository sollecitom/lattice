package sollecitom.libs.lattice.command_gateway.local.tests

import sollecitom.libs.lattice.aggregate.domain.Aggregate
import sollecitom.libs.lattice.aggregate.domain.AggregateType
import sollecitom.libs.lattice.aggregate.domain.CommandDecision

object CounterAggregate : Aggregate<CounterCommand, CounterEvent, CounterState> {

    override val type = AggregateType("counter")
    override val initialState = CounterState(value = 0)

    override fun decide(state: CounterState, command: CounterCommand): CommandDecision<CounterEvent> = when (command) {
        is CounterCommand.Increment -> CommandDecision.Accepted(CounterEvent.Incremented(command.amount))
        is CounterCommand.Decrement -> {
            if (state.value >= command.amount) {
                CommandDecision.Accepted(CounterEvent.Decremented(command.amount))
            } else {
                CommandDecision.Rejected("Cannot decrement below zero")
            }
        }
    }

    override fun evolve(state: CounterState, event: CounterEvent): CounterState = when (event) {
        is CounterEvent.Incremented -> state.copy(value = state.value + event.amount)
        is CounterEvent.Decremented -> state.copy(value = state.value - event.amount)
    }
}

sealed interface CounterCommand {
    data class Increment(val amount: Int = 1) : CounterCommand
    data class Decrement(val amount: Int = 1) : CounterCommand
}

sealed interface CounterEvent {
    data class Incremented(val amount: Int) : CounterEvent
    data class Decremented(val amount: Int) : CounterEvent
}

data class CounterState(val value: Int)
