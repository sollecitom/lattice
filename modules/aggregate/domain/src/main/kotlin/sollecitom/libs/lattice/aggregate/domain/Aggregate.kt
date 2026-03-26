package sollecitom.libs.lattice.aggregate.domain

interface Aggregate<COMMAND, EVENT, STATE> {

    val type: AggregateType
    val initialState: STATE

    fun decide(state: STATE, command: COMMAND): CommandDecision<EVENT>

    fun evolve(state: STATE, event: EVENT): STATE

    companion object
}

fun <COMMAND, EVENT, STATE> Aggregate<COMMAND, EVENT, STATE>.rehydrate(events: List<EVENT>): STATE =
    events.fold(initialState) { state, event -> evolve(state, event) }
