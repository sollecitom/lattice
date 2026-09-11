package sollecitom.lattice.core

sealed interface Decision<out EVENT : DomainEvent> {

    data class Accept<EVENT : DomainEvent>(val event: EVENT) : Decision<EVENT>

    data class Reject(val reason: String) : Decision<Nothing>
}

fun <EVENT : DomainEvent> accept(event: EVENT): Decision<EVENT> = Decision.Accept(event)

fun reject(reason: String): Decision<Nothing> = Decision.Reject(reason)

interface Aggregate<COMMAND : Command, EVENT : DomainEvent, STATE> {

    val id: String

    val initialState: STATE

    fun decide(state: STATE, command: COMMAND): Decision<EVENT>

    fun apply(state: STATE, event: EVENT): STATE
}

sealed interface ReadModel<QUERY : Query<*>> {

    val id: String
}

sealed interface ProjectingReadModel<QUERY : Query<*>> : ReadModel<QUERY>

interface MaterialisingReadModel<EVENT : DomainEvent, QUERY : Query<ANSWER>, ANSWER> : ProjectingReadModel<QUERY> {

    suspend fun apply(event: EVENT)

    suspend fun answer(query: QUERY): ANSWER
}

interface EventSourcedReadModel<EVENT : DomainEvent, STATE, QUERY : Query<ANSWER>, ANSWER> : ProjectingReadModel<QUERY> {

    val initialState: STATE

    fun apply(state: STATE, event: EVENT): STATE

    fun answer(state: STATE, query: QUERY): ANSWER
}
