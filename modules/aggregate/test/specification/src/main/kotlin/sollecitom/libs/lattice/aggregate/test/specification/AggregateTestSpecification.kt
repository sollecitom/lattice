package sollecitom.libs.lattice.aggregate.test.specification

import assertk.assertThat
import assertk.assertions.*
import org.junit.jupiter.api.Test
import sollecitom.libs.lattice.aggregate.domain.*

@Suppress("FunctionName")
interface AggregateTestSpecification<COMMAND, EVENT, STATE> {

    val aggregate: Aggregate<COMMAND, EVENT, STATE>

    fun acceptedCommand(state: STATE): COMMAND
    fun rejectedCommand(state: STATE): COMMAND
    fun expectedEventsForAcceptedCommand(state: STATE, command: COMMAND): List<EVENT>
    fun stateAfterEvents(events: List<EVENT>): STATE

    @Test
    fun `an accepted command produces events`() {

        val state = aggregate.initialState
        val command = acceptedCommand(state)

        val decision = aggregate.decide(state, command)

        assertThat(decision).isInstanceOf<CommandDecision.Accepted<EVENT>>()
        val accepted = decision as CommandDecision.Accepted<EVENT>
        assertThat(accepted.events).isNotEmpty()
    }

    @Test
    fun `a rejected command produces a rejection reason`() {

        val state = aggregate.initialState
        val command = rejectedCommand(state)

        val decision = aggregate.decide(state, command)

        assertThat(decision).isInstanceOf<CommandDecision.Rejected>()
    }

    @Test
    fun `events can be used to evolve state`() {

        val state = aggregate.initialState
        val command = acceptedCommand(state)
        val decision = aggregate.decide(state, command) as CommandDecision.Accepted<EVENT>

        val newState = decision.events.fold(state) { currentState, event -> aggregate.evolve(currentState, event) }

        val expectedState = stateAfterEvents(decision.events)
        assertThat(newState).isEqualTo(expectedState)
    }

    @Test
    fun `rehydrating from events produces the same state as evolving step by step`() {

        val state = aggregate.initialState
        val command = acceptedCommand(state)
        val decision = aggregate.decide(state, command) as CommandDecision.Accepted<EVENT>

        val rehydrated = aggregate.rehydrate(decision.events)
        val evolvedStepByStep = decision.events.fold(state) { currentState, event -> aggregate.evolve(currentState, event) }

        assertThat(rehydrated).isEqualTo(evolvedStepByStep)
    }

    @Test
    fun `initial state is the starting point`() {

        val rehydrated = aggregate.rehydrate(emptyList())

        assertThat(rehydrated).isEqualTo(aggregate.initialState)
    }
}
