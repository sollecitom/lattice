package sollecitom.libs.lattice.aggregate.domain

interface IdempotentAggregate<COMMAND, EVENT, STATE> : Aggregate<COMMAND, EVENT, STATE> {

    fun commandId(command: COMMAND): CommandId?

    fun hasProcessed(state: STATE, commandId: CommandId): Boolean

    companion object
}

fun <COMMAND, EVENT, STATE> IdempotentAggregate<COMMAND, EVENT, STATE>.decideIdempotently(state: STATE, command: COMMAND): CommandDecision<EVENT> {

    val id = commandId(command)
    if (id != null && hasProcessed(state, id)) return CommandDecision.Accepted(emptyList())
    return decide(state, command)
}
