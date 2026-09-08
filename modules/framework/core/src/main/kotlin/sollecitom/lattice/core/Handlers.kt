package sollecitom.lattice.core

sealed interface Decision<out EVENT : DomainEvent> {

    data class Accept<EVENT : DomainEvent>(val event: EVENT) : Decision<EVENT>

    data class Reject(val reason: String) : Decision<Nothing>
}

fun <EVENT : DomainEvent> accept(event: EVENT): Decision<EVENT> = Decision.Accept(event)

fun reject(reason: String): Decision<Nothing> = Decision.Reject(reason)

interface Aggregate<COMMAND : Command, EVENT : DomainEvent, STATE> {

    val initialState: STATE

    fun decide(state: STATE, command: COMMAND): Decision<EVENT>

    fun apply(state: STATE, event: EVENT): STATE
}

interface ReadModel<EVENT : DomainEvent, STATE, QUERY : Query<*>> {

    val initialState: STATE

    fun apply(state: STATE, event: EVENT): STATE

    fun <ANSWER> answer(state: STATE, query: Query<ANSWER>): ANSWER
}
