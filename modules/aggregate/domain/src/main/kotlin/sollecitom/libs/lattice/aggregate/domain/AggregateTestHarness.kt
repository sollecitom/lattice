package sollecitom.libs.lattice.aggregate.domain

class AggregateTestHarness<COMMAND, EVENT, STATE>(private val aggregate: Aggregate<COMMAND, EVENT, STATE>) {

    private var state: STATE = aggregate.initialState
    private val emittedEvents = mutableListOf<EVENT>()

    val currentState: STATE get() = state
    val events: List<EVENT> get() = emittedEvents.toList()

    fun given(vararg events: EVENT): AggregateTestHarness<COMMAND, EVENT, STATE> = apply {
        events.forEach { event ->
            state = aggregate.evolve(state, event)
            emittedEvents.add(event)
        }
    }

    fun `when`(command: COMMAND): CommandDecision<EVENT> {
        val decision = aggregate.decide(state, command)
        if (decision is CommandDecision.Accepted) {
            decision.events.forEach { event ->
                state = aggregate.evolve(state, event)
                emittedEvents.add(event)
            }
        }
        return decision
    }

    fun reset(): AggregateTestHarness<COMMAND, EVENT, STATE> = apply {
        state = aggregate.initialState
        emittedEvents.clear()
    }

    companion object
}

fun <COMMAND, EVENT, STATE> Aggregate<COMMAND, EVENT, STATE>.testHarness() = AggregateTestHarness(this)
